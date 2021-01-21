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
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class PipelineGatingRestartTest {

    @Rule public final RestartableJenkinsRule j = new RestartableJenkinsRule();

    @Test
    public void restartBlocked() {
        PipelineGatingTest.Runner[] r = new PipelineGatingTest.Runner[1];
        j.then(j -> {
            WorkflowJob w = j.jenkins.createProject(WorkflowJob.class, "w");
            w.setDefinition(new CpsFlowDefinition(
                    "echo 'Bstart'; requireResources(resources: ['foo/bar/baz', 'foo/red/sox']) { echo 'Binside' }; echo 'Bafter'", true
            ));

            r[0] = new PipelineGatingTest.Runner(w, j);
            r[0].await("Bstart", "Binside");
            r[0].await("Some resources are not available: foo/bar/baz is UNKNOWN, foo/red/sox is UNKNOWN");
        });

        j.then(j -> {
            Utils.setStatus(ImmutableMap.of(
                    "foo/bar/baz", ResourceStatus.Category.UP,
                    "foo/red/sox", ResourceStatus.Category.DOWN
            ));

            r[0].await("Bstart", "Binside");
            r[0].await("Some resources are not available: foo/red/sox is DOWN");
        });

        j.then(j -> {
            Utils.setStatus(ImmutableMap.of(
                    "foo/bar/baz", ResourceStatus.Category.UP,
                    "foo/red/sox", ResourceStatus.Category.UP
            ));

            r[0].await("Binside");
            r[0].await("Bafter");
        });
    }
}
