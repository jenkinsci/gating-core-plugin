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

import com.google.common.collect.ImmutableSet;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hudson.ExtensionList.lookupSingleton;
import static io.jenkins.plugins.gating.GatingMetrics.get;
import static io.jenkins.plugins.gating.Utils.snapshot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class GatingMetricsTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    @TestExtension public static class AMetricsProvider extends Provider { public AMetricsProvider() { super("a"); } }
    @TestExtension public static class BMetricsProvider extends Provider { public BMetricsProvider() { super("b", "bb"); } }
    @TestExtension public static class XMetricsProvider extends Provider { public XMetricsProvider() { super("x", "bb"); } }

    @Test
    public void updateWithOverlappingSourceLabels() throws Exception {
        GatingMetrics gm = get();

        MetricsProvider pa = lookupSingleton(AMetricsProvider.class);
        MetricsProvider pb = lookupSingleton(BMetricsProvider.class);
        MetricsProvider px = lookupSingleton(XMetricsProvider.class);

        MetricsSnapshot pau = Utils.snapshot(pa, "a/a", ResourceStatus.Category.UP);
        gm.update(pau);
        gm.update(pau);

        MetricsSnapshot pbu = Utils.snapshot(pb, "bb/bb", ResourceStatus.Category.DOWN);
        gm.update(pbu);

        // Colliding config, but unique labels reported
        MetricsSnapshot pxu = Utils.snapshot(px, "x/x", ResourceStatus.Category.UP);
        gm.update(pxu);

        MetricsSnapshot expected = gm.getMetrics().get("bb");
        pxu = Utils.snapshot(px, "bb/bb", ResourceStatus.Category.UP);
        gm.update(pxu);
        assertSame(expected, gm.getMetrics().get("bb"));

        JenkinsRule.WebClient wc = j.createWebClient();
        String text = wc.goTo("gating").getBody().getTextContent();
        assertThat(text, containsString("have a colliding sourceLabel bb. Ignoring metrics update."));
    }

    @Test
    public void reportErrorsWithProviderMismatch() {
        GatingMetrics gm = get();

        MetricsProvider pa = lookupSingleton(AMetricsProvider.class);
        MetricsProvider pb = lookupSingleton(BMetricsProvider.class);

        gm.reportError(new MetricsSnapshot.Error(pa, "foo", "problem 1", null));
        gm.reportError(new MetricsSnapshot.Error(pa, "foo", "problem 2", null));
        gm.reportError(new MetricsSnapshot.Error(pb, "foo", "problem 3", null));

        // Unknown error should be ignored
        Map<String, MetricsSnapshot.Error> errors = gm.getErrors();
        assertEquals(ImmutableSet.of("foo"), errors.keySet());
        assertSame(pa, errors.get("foo").getProvider());
        assertEquals("problem 2", errors.get("foo").getMessage());

        // Unknown update should be ignored
        gm.update(new MetricsSnapshot(pb, "foo", Collections.emptyMap()));

        assertThat(gm.getMetrics(), anEmptyMap());
    }

    @Test
    public void updateWithProviderMismatch() {
        GatingMetrics gm = get();

        MetricsProvider pa = lookupSingleton(AMetricsProvider.class);
        MetricsProvider pb = lookupSingleton(BMetricsProvider.class);

        gm.update(new MetricsSnapshot(pa, "foo", Collections.emptyMap()));
        gm.update(new MetricsSnapshot(pb, "foo", Collections.emptyMap()));

        // Unknown update should be ignored
        Map<String, MetricsSnapshot> metrics = gm.getMetrics();
        assertEquals(ImmutableSet.of("foo"), metrics.keySet());
        assertSame(pa, metrics.get("foo").getProvider());

        gm.reportError(new MetricsSnapshot.Error(pb, "foo", "problem 1", null));
        assertThat(gm.getErrors(), anEmptyMap());
    }

    @Test
    public void ui() throws Exception {

        JenkinsRule.WebClient wc = j.createWebClient();
        GatingMetrics gm = get();
        gm.update(snapshot("statuspage/pageA/resourceC", TestStatus.OK));
        gm.update(snapshot("cachet/resource1", TestStatus.DECENT));
        gm.update(snapshot(
                "zabbix/host1.exeample.com", TestStatus.BELLY_UP,
                "zabbix/host2.exeample.com", TestStatus.OK
        ));

        MetricsProvider cachetProvider = gm.getMetrics().get("cachet").getProvider();
        gm.reportError(new MetricsSnapshot.Error(
                cachetProvider, "cachet", "Failed fetching data", new RuntimeException("Cause message")
        ));

        gm.reportError(new MetricsSnapshot.Error(
                new MetricsProvider() {
                    @Nonnull @Override public Set<String> getLabels() {
                        return ImmutableSet.of("justerror");
                    }
                }, "justerror", "No data; just error", new RuntimeException("Just error")
        ));

        String gating = wc.goTo("gating").getBody().getTextContent();

        assertThat(gating, containsString("zabbix/host1.exeample.comBELLY_UP"));
        assertThat(gating, containsString("zabbix/host2.exeample.comOK"));
        assertThat(gating, containsString("statuspage/pageA/resourceCOK"));
        assertThat(gating, containsString("cachet/resource1DECENT"));

        assertThat(gating, containsString("cachetFailed fetching data"));
        assertThat(gating, containsString("RuntimeException: Cause message"));
        assertThat(gating, containsString("No data; just error"));
        assertThat(gating, containsString("RuntimeException: Just error"));
    }

    public static class Provider implements MetricsProvider {

        private Set<String> labels;

        public Provider(String... labels) {
            this.labels = Stream.of(labels).collect(Collectors.toSet());
        }

        @Nonnull @Override public Set<String> getLabels() {
            return labels;
        }
    }
}
