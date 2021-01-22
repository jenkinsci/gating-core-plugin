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

import hudson.ExtensionList;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class GatingMatricesTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    @TestExtension public static class AMatricesProvider extends Provider { public AMatricesProvider() { super("a"); } }
    @TestExtension public static class BMatricesProvider extends Provider { public BMatricesProvider() { super("b", "bb"); } }
    @TestExtension public static class XMatricesProvider extends Provider { public XMatricesProvider() { super("x", "bb"); } }

    @Test
    public void collide() throws Exception {
        GatingMatrices gm = GatingMatrices.get();

        MatricesProvider pa = ExtensionList.lookupSingleton(AMatricesProvider.class);
        MatricesProvider pb = ExtensionList.lookupSingleton(BMatricesProvider.class);
        MatricesProvider px = ExtensionList.lookupSingleton(XMatricesProvider.class);

        GatingMatrices.Snapshot pau = new GatingMatrices.Snapshot(pa, "a", Collections.singletonMap("a/a", ResourceStatus.Category.UP));
        gm.update(pau);
        gm.update(pau);

        GatingMatrices.Snapshot pbu = new GatingMatrices.Snapshot(pb, "bb", Collections.singletonMap("bb/bb", ResourceStatus.Category.DOWN));
        gm.update(pbu);

        // Colliding config, but unique labels reported
        GatingMatrices.Snapshot pxu = new GatingMatrices.Snapshot(px, "x", Collections.singletonMap("x/x", ResourceStatus.Category.UP));
        gm.update(pxu);

        GatingMatrices.Snapshot expected = gm.getMatrices().get("bb");
        pxu = new GatingMatrices.Snapshot(px, "bb", Collections.singletonMap("bb/bb", ResourceStatus.Category.UP));
        gm.update(pxu);
        assertSame(expected, gm.getMatrices().get("bb"));


        j.interactiveBreak(); // TEST report in UI
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
