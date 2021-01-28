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
import static io.jenkins.plugins.gating.GatingMatrices.get;
import static io.jenkins.plugins.gating.Utils.snapshot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class GatingMatricesTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    @TestExtension public static class AMatricesProvider extends Provider { public AMatricesProvider() { super("a"); } }
    @TestExtension public static class BMatricesProvider extends Provider { public BMatricesProvider() { super("b", "bb"); } }
    @TestExtension public static class XMatricesProvider extends Provider { public XMatricesProvider() { super("x", "bb"); } }

    @Test
    public void updateWithOverlappingSourceLabels() throws Exception {
        GatingMatrices gm = get();

        MatricesProvider pa = lookupSingleton(AMatricesProvider.class);
        MatricesProvider pb = lookupSingleton(BMatricesProvider.class);
        MatricesProvider px = lookupSingleton(XMatricesProvider.class);

        MatricesSnapshot pau = Utils.snapshot(pa, "a/a", ResourceStatus.Category.UP);
        gm.update(pau);
        gm.update(pau);

        MatricesSnapshot pbu = Utils.snapshot(pb, "bb/bb", ResourceStatus.Category.DOWN);
        gm.update(pbu);

        // Colliding config, but unique labels reported
        MatricesSnapshot pxu = Utils.snapshot(px, "x/x", ResourceStatus.Category.UP);
        gm.update(pxu);

        MatricesSnapshot expected = gm.getMatrices().get("bb");
        pxu = Utils.snapshot(px, "bb/bb", ResourceStatus.Category.UP);
        gm.update(pxu);
        assertSame(expected, gm.getMatrices().get("bb"));

        JenkinsRule.WebClient wc = j.createWebClient();
        String text = wc.goTo("gating").getBody().getTextContent();
        assertThat(text, containsString("have a colliding sourceLabel bb. Ignoring matrices update."));
    }

    @Test
    public void reportErrorsWithProviderMismatch() {
        GatingMatrices gm = get();

        MatricesProvider pa = lookupSingleton(AMatricesProvider.class);
        MatricesProvider pb = lookupSingleton(BMatricesProvider.class);

        gm.reportError(new MatricesSnapshot.Error(pa, "foo", "problem 1", null));
        gm.reportError(new MatricesSnapshot.Error(pa, "foo", "problem 2", null));
        gm.reportError(new MatricesSnapshot.Error(pb, "foo", "problem 3", null));

        // Unknown error should be ignored
        Map<String, MatricesSnapshot.Error> errors = gm.getErrors();
        assertEquals(ImmutableSet.of("foo"), errors.keySet());
        assertSame(pa, errors.get("foo").getProvider());
        assertEquals("problem 2", errors.get("foo").getMessage());

        // Unknown update should be ignored
        gm.update(new MatricesSnapshot(pb, "foo", Collections.emptyMap()));

        assertThat(gm.getMatrices(), anEmptyMap());
    }

    @Test
    public void updateWithProviderMismatch() {
        GatingMatrices gm = get();

        MatricesProvider pa = lookupSingleton(AMatricesProvider.class);
        MatricesProvider pb = lookupSingleton(BMatricesProvider.class);

        gm.update(new MatricesSnapshot(pa, "foo", Collections.emptyMap()));
        gm.update(new MatricesSnapshot(pb, "foo", Collections.emptyMap()));

        // Unknown update should be ignored
        Map<String, MatricesSnapshot> matrices = gm.getMatrices();
        assertEquals(ImmutableSet.of("foo"), matrices.keySet());
        assertSame(pa, matrices.get("foo").getProvider());

        gm.reportError(new MatricesSnapshot.Error(pb, "foo", "problem 1", null));
        assertThat(gm.getErrors(), anEmptyMap());
    }

    @Test
    public void ui() throws Exception {

        JenkinsRule.WebClient wc = j.createWebClient();
        GatingMatrices gm = get();
        gm.update(snapshot("statuspage/pageA/resourceC", TestStatus.OK));
        gm.update(snapshot("cachet/resource1", TestStatus.DECENT));
        gm.update(snapshot(
                "zabbix/host1.exeample.com", TestStatus.BELLY_UP,
                "zabbix/host2.exeample.com", TestStatus.OK
        ));

        MatricesProvider cachetProvider = gm.getMatrices().get("cachet").getProvider();
        gm.reportError(new MatricesSnapshot.Error(
                cachetProvider, "cachet", "Failed fetching data", new RuntimeException("Cause message")
        ));

        gm.reportError(new MatricesSnapshot.Error(
                new MatricesProvider() {
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

    public static class Provider implements MatricesProvider {

        private Set<String> labels;

        public Provider(String... labels) {
            this.labels = Stream.of(labels).collect(Collectors.toSet());
        }

        @Nonnull @Override public Set<String> getLabels() {
            return labels;
        }
    }
}
