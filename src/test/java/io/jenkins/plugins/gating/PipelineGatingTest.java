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
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class PipelineGatingTest {

    @Rule
    public final JenkinsRule j  = new JenkinsRule();

    @Test
    public void blockWhenDown() throws Exception {
        WorkflowJob w = j.jenkins.createProject(WorkflowJob.class, "w");
        w.setDefinition(new CpsFlowDefinition(
                "echo 'Bstart'; requireResources(resources: ['foo/bar/baz', 'foo/red/sox']) { echo 'Binside' }; echo 'Bafter'", true
        ));

        Runner r = new Runner(w, j);
        r.await("Bstart", "Binside");
        r.await("Some resources are not available: foo/bar/baz is UNKNOWN, foo/red/sox is UNKNOWN");

        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", ResourceStatus.Category.UP,
                "foo/red/sox", ResourceStatus.Category.DOWN
        ));

        r.await("Bstart", "Binside");
        r.await("Some resources are not available: foo/red/sox is DOWN");

        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", ResourceStatus.Category.UP,
                "foo/red/sox", ResourceStatus.Category.UP
        ));

        r.await("Binside");
        r.await("Bafter");

        j.assertBuildStatusSuccess(r.run);
    }

    @Test
    public void passWhenUp() throws Exception {
        Utils.setStatus(ImmutableMap.of(
                "foo/bar/baz", ResourceStatus.Category.UP,
                "foo/red/sox", ResourceStatus.Category.UP
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
