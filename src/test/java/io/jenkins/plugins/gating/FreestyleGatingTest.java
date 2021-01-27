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

import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static io.jenkins.plugins.gating.ResourceStatus.Category.UP;
import static io.jenkins.plugins.gating.Utils.snapshot;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FreestyleGatingTest {

    public static final String RES1 = "statuspage/pageA/my-resource";
    public static final String RES2 = "statuspage/Page #3/The Resource";

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Test
    public void allDown() throws Exception {
        MatricesSnapshot snapshot = snapshot(
                RES1, TestStatus.BELLY_UP,
                RES2, TestStatus.DECENT
        );

        Queue.Item item = runJob(snapshot, new ResourceRequirementProperty(asList(RES1, RES2)));
        CauseOfBlockage cob = item.getCauseOfBlockage();
        assertEquals(
                String.format("Some resources are not available: %s is BELLY_UP, %s is DECENT", RES1, RES2),
                cob.getShortDescription()
        );
    }

    @Test
    public void allUp() throws Exception {
        MatricesSnapshot snapshot = snapshot(
                RES1, TestStatus.OK,
                RES2, TestStatus.OK
        );

        runJob(snapshot, new ResourceRequirementProperty(Collections.singletonList(RES2)));
        assertTrue(j.getInstance().getQueue().isEmpty());
    }

    @Test
    public void someDown() throws Exception {
        MatricesSnapshot snapshot = snapshot(
                RES1, TestStatus.BELLY_UP,
                RES2, TestStatus.OK
        );

        Queue.Item item = runJob(snapshot, new ResourceRequirementProperty(asList(RES1, RES2)));
        CauseOfBlockage cob = item.getCauseOfBlockage();
        assertEquals(
                String.format("Some resources are not available: %s is BELLY_UP", RES1),
                cob.getShortDescription()
        );
    }

    @Test
    public void configRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        List<String> expected;

        // Nothing configured; nothing reported
        j.configRoundtrip(p);
        assertNull(p.getProperty(ResourceRequirementProperty.class));

        expected = Collections.emptyList();
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResources());

        // Something configured; nothing reported
        expected = asList(RES2, RES1);
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResources());

        // Something configured; something reported
        Utils.setStatus(Utils.snapshot(
                RES1, TestStatus.OK,
                RES2, TestStatus.BELLY_UP
        ));

        expected = asList(RES2, RES1);
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResources());

        expected = Collections.singletonList(RES2);
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResources());

        // Nothing configured; something reported
        expected = Collections.emptyList();
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResources());

        p.removeProperty(ResourceRequirementProperty.class);
        j.configRoundtrip(p);
        assertNull(p.getProperty(ResourceRequirementProperty.class));
    }

    @Test
    public void ui() throws Exception {

        JenkinsRule.WebClient wc = j.createWebClient();
        GatingMatrices gm = GatingMatrices.get();
        gm.update(snapshot("statuspage/pageA/resourceC", TestStatus.OK));
        gm.update(snapshot("cachet/resource1", TestStatus.DECENT));
        gm.update(snapshot(
                "zabbix/host1.exeample.com", TestStatus.BELLY_UP,
                "zabbix/host2.exeample.com", TestStatus.OK
        ));

        String gating = wc.goTo("gating").getBody().getTextContent();

        assertThat(gating, containsString("zabbix/host1.exeample.comBELLY_UP"));
        assertThat(gating, containsString("zabbix/host2.exeample.comOK"));
        assertThat(gating, containsString("statuspage/pageA/resourceCOK"));
        assertThat(gating, containsString("cachet/resource1DECENT"));
    }

    @Test
    public void jobDslFreestyle() throws Exception {
        FreeStyleProject seed = j.createFreeStyleProject();
        ExecuteDslScripts jobdsl = new ExecuteDslScripts();
        jobdsl.setScriptText("job('foo') { properties { requireResources { resources(['foo/bar/baz', 'foo/red/sox']) } } }");
        seed.getBuildersList().add(jobdsl);

        j.buildAndAssertSuccess(seed);

        FreeStyleProject target = j.jenkins.getItem("foo", j.jenkins, FreeStyleProject.class);
        ResourceRequirementProperty rrp = target.getProperty(ResourceRequirementProperty.class);
        assertEquals(asList("foo/bar/baz", "foo/red/sox"), rrp.getResources());

        target.scheduleBuild2(0);
        Thread.sleep(1000);
        Queue.Item item = j.getInstance().getQueue().getItem(target);

        assertThat(item.getCauseOfBlockage(), instanceOf(ResourceBlockage.class));

        Utils.setStatus(Utils.snapshot(
                "foo/bar/baz", UP,
                "foo/red/sox", UP
        ));

        item.getFuture().get();
    }

    private Queue.Item runJob(MatricesSnapshot status, JobProperty<? super FreeStyleProject> reqs) throws IOException, InterruptedException {
        Utils.setStatus(status);

        FreeStyleProject p = j.createFreeStyleProject();

        p.addProperty(reqs);

        p.scheduleBuild2(0);

        Queue queue = j.getInstance().getQueue();

        while (true) {

            // Build
            if (p.getBuildByNumber(1) != null) return null;

            // Blocked
            Queue.Item item = queue.getItem(p);
            if (item != null && item.getCauseOfBlockage() instanceof ResourceBlockage) return item;

            Thread.sleep(1000);
        }
    }
}
