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
import io.jenkins.plugins.gating.GatingMatrices;
import io.jenkins.plugins.gating.ResourceBlockage;
import io.jenkins.plugins.gating.ResourceRequirementProperty;
import io.jenkins.plugins.gating.ResourceStatus;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.jenkins.plugins.gating.ResourceStatus.Category.DEGRADED;
import static io.jenkins.plugins.gating.ResourceStatus.Category.DOWN;
import static io.jenkins.plugins.gating.ResourceStatus.Category.UP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FreestyleGatingTest {

    public static final String RESOURCE_NAME = "statuspage/p1/C1/r1";
    public static final String COMPONENT_NAME = "statuspage/p2/C3";

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Test
    public void allDown() throws Exception {

        Map<String, ResourceStatus> status = new HashMap<>();
        status.put(RESOURCE_NAME, MyStatus.BELLY_UP);
        status.put(COMPONENT_NAME, MyStatus.DECENT);

        Queue.Item item = runJob(status, new ResourceRequirementProperty(Arrays.asList(RESOURCE_NAME, COMPONENT_NAME)));
        CauseOfBlockage cob = item.getCauseOfBlockage();
        assertEquals(
                String.format("Some resource are not available: %s is BELLY_UP, %s is DECENT", RESOURCE_NAME, COMPONENT_NAME),
                cob.getShortDescription()
        );
    }

    @Test
    public void allUp() throws Exception {
        HashMap<String, ResourceStatus> status = new HashMap<>();
        status.put(RESOURCE_NAME, MyStatus.OK);
        status.put(COMPONENT_NAME, MyStatus.OK);

        runJob(status, new ResourceRequirementProperty(Collections.singletonList(COMPONENT_NAME)));
        assertTrue(j.getInstance().getQueue().isEmpty());
    }

    @Test
    public void someDown() throws Exception {

        Map<String, ResourceStatus> status = new HashMap<>();
        status.put(RESOURCE_NAME, MyStatus.BELLY_UP);
        status.put(COMPONENT_NAME, MyStatus.OK);

        Queue.Item item = runJob(status, new ResourceRequirementProperty(Arrays.asList(RESOURCE_NAME, COMPONENT_NAME)));
        CauseOfBlockage cob = item.getCauseOfBlockage();
        assertEquals(
                String.format("Some resource are not available: %s is BELLY_UP", RESOURCE_NAME),
                cob.getShortDescription()
        );
    }

    @Test
    public void configRoundtrip() throws Exception {
        Map<String, ResourceStatus> status = new HashMap<>();
        status.put(RESOURCE_NAME, MyStatus.OK);
        status.put(COMPONENT_NAME, MyStatus.BELLY_UP);
        setStatus(status);

        FreeStyleProject p = j.createFreeStyleProject();
        List<String> expected;

        expected = Arrays.asList(RESOURCE_NAME, COMPONENT_NAME);
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResouces());

        expected = Collections.singletonList(COMPONENT_NAME);
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResouces());

        expected = Collections.emptyList();
        p.addProperty(new ResourceRequirementProperty(expected));
        j.configRoundtrip(p);
        assertEquals(expected, p.getProperty(ResourceRequirementProperty.class).getResouces());
    }

    @Test
    public void ui() throws Exception {
        GatingMatrices gm = GatingMatrices.get();
        gm.update("statuspage", GatingMatrices.Snapshot.with("statuspage/pageA/componentB/resourceC", MyStatus.OK));
        gm.update("cachet", GatingMatrices.Snapshot.with("cachet/resource1", MyStatus.DECENT));
        gm.update("zabbix", GatingMatrices.Snapshot
                .with("zabbix/host1.exeample.com", MyStatus.BELLY_UP)
                .and("zabbix/host2.exeample.com", MyStatus.OK)
        );

        JenkinsRule.WebClient wc = j.createWebClient();
        String gating = wc.goTo("gating").getBody().getTextContent();

        assertThat(gating, containsString("zabbix/host1.exeample.comBELLY_UP"));
        assertThat(gating, containsString("zabbix/host2.exeample.comOK"));
        assertThat(gating, containsString("statuspage/pageA/componentB/resourceCOK"));
        assertThat(gating, containsString("cachet/resource1DECENT"));
    }

    public enum MyStatus implements ResourceStatus {
        OK(UP), DECENT(DEGRADED), BELLY_UP(DOWN);

        private final ResourceStatus.Category category;

        MyStatus(ResourceStatus.Category category) {
            this.category = category;
        }

        @Override
        public @Nonnull Category getCategory() {
            return category;
        }
    }

    private Queue.Item runJob(
            Map<String, ResourceStatus> status,
            JobProperty<? super FreeStyleProject> reqs
    ) throws IOException, InterruptedException {
        setStatus(status);

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

    private void setStatus(Map<String, ResourceStatus> status) {
        GatingMatrices.get().update("statuspage", new GatingMatrices.Snapshot(status));
    }
}
