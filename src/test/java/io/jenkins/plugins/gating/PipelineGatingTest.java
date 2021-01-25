/*
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.jenkins.plugins.gating;

import com.google.common.collect.ImmutableMap;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import org.apache.tools.ant.filters.StringInputStream;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.UnitTestSupportingPluginManager;
import org.jvnet.hudson.test.recipes.WithPluginManager;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.jar.Manifest;

import static io.jenkins.plugins.gating.ResourceStatus.Category.UP;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class PipelineGatingTest {

    @Rule
    public final JenkinsRule j  = new JenkinsRule();

    @Test
    // Job DSL plugin fail on checking the plugin is properly installed through Jenkins, which it is not in jenkins-test-harness.
    // Pretend it is installed well enough for the check to pass.
    @WithPluginManager(FakingPluginManager.class)
    public void jobDslProperty() throws Exception {

        FreeStyleProject seed = j.createFreeStyleProject();
        ExecuteDslScripts jobdsl = new ExecuteDslScripts();
        jobdsl.setScriptText("pipelineJob('foo') { properties { requireResources { resources(['foo/bar/baz', 'foo/red/sox']) } } }");
        seed.getBuildersList().add(jobdsl);

        j.buildAndAssertSuccess(seed);

        WorkflowJob target = j.jenkins.getItem("foo", j.jenkins, WorkflowJob.class);
        ResourceRequirementProperty rrp = target.getProperty(ResourceRequirementProperty.class);
        assertEquals(asList("foo/bar/baz", "foo/red/sox"), rrp.getResources());

        target.scheduleBuild2(0);
        Thread.sleep(1000);
        Queue.Item item = j.getInstance().getQueue().getItem(target);

        assertThat(item.getCauseOfBlockage(), Matchers.instanceOf(ResourceBlockage.class));

        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", UP,
                "foo/red/sox", UP
        ));

        item.getFuture().get();
    }
    public static final class FakingPluginManager extends UnitTestSupportingPluginManager {
        public FakingPluginManager(File rootDir) {
            super(rootDir);
        }

        @Override
        public @CheckForNull PluginWrapper getPlugin(String shortName) {
            if ("workflow-job".equals(shortName)) {
                try {
                    Manifest manifest = new Manifest(new StringInputStream("Short-Name: workflow-job"));
                    PluginWrapper pluginWrapper = new PluginWrapper(
                            this,
                            new File("/fake/workflow-job.hpi"),
                            manifest,
                            null,
                            getClass().getClassLoader(),
                            new File("/fake/workflow-job.hpi.disable"),
                            Collections.emptyList(),
                            Collections.emptyList()
                    ) {
                        @Override public Plugin getPlugin() {
                            return new Plugin() {};
                        }
                    };
                    return pluginWrapper;
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
            return super.getPlugin(shortName);
        }
    }

    @Test
    public void blockStepWhenDown() throws Exception {
        WorkflowJob w = j.jenkins.createProject(WorkflowJob.class, "w");
        w.setDefinition(new CpsFlowDefinition(
                "echo 'Bstart'; requireResources(resources: ['foo/bar/baz', 'foo/red/sox']) { echo 'Binside' }; echo 'Bafter'", true
        ));

        Runner r = new Runner(w, j);
        r.await("Bstart", "Binside");
        r.await("Some resources are not available: foo/bar/baz is UNKNOWN, foo/red/sox is UNKNOWN");

        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", UP,
                "foo/red/sox", ResourceStatus.Category.DOWN
        ));

        r.await("Bstart", "Binside");
        r.await("Some resources are not available: foo/red/sox is DOWN");

        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", UP,
                "foo/red/sox", UP
        ));

        r.await("Binside");
        r.await("Bafter");

        j.assertBuildStatusSuccess(r.run);
    }

    @Test
    public void passStepWhenUp() throws Exception {
        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", UP,
                "foo/red/sox", UP
        ));

        WorkflowJob w = j.jenkins.createProject(WorkflowJob.class, "w");
        w.setDefinition(new CpsFlowDefinition(
                "echo 'Bstart'; requireResources(resources: ['foo/bar/baz', 'foo/red/sox']) { echo 'Binside' }; echo 'Bafter'", true
        ));

        j.buildAndAssertSuccess(w);
    }

    public static final class Runner {

        private final WorkflowJob job;
        private final WorkflowRun run;
        private final JenkinsRule j;

        public Runner(WorkflowJob w, JenkinsRule j) throws ExecutionException, InterruptedException {
            job = w;
            run = w.scheduleBuild2(0).getStartCondition().get();
            this.j = j;
        }

        public void await(String present, String absent) throws IOException, InterruptedException {
            await(present);
            j.assertLogNotContains(absent, run);
        }

        public void await(String present) throws IOException, InterruptedException {
            for (int i = 0; ; i++) {
                try {
                    j.assertLogContains(present, run);
                    break;
                } catch (AssertionError ae) {
                    if (i < 10) {
                        Thread.sleep(1000);
                        continue;
                    }

                    throw ae;
                }
            }
        }
    }
}
