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

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Utils {

    static void setStatus(Map<String, MetricsSnapshot.Resource> status) {
        GatingMetrics.get().update(snapshot(status));
    }

    static void setStatus(MetricsSnapshot snapshot) {
        GatingMetrics.get().update(snapshot);
    }

    public static MetricsSnapshot snapshot(String name, ResourceStatus rs) {
        HashMap<String, MetricsSnapshot.Resource> hm = new HashMap<>();
        hm.put(name, resource(name, rs));
        return snapshot(hm);
    }

    public static MetricsSnapshot snapshot(String name0, ResourceStatus rs0, String name1, ResourceStatus rs1) {
        HashMap<String, MetricsSnapshot.Resource> hm = new HashMap<>();
        hm.put(name0, resource(name0, rs0));
        hm.put(name1, resource(name1, rs1));
        return snapshot(hm);
    }

    public static MetricsSnapshot snapshot(MetricsProvider provider, String name0, ResourceStatus rs0, String name1, ResourceStatus rs1) {
        HashMap<String, MetricsSnapshot.Resource> hm = new HashMap<>();
        hm.put(name0, resource(name0, rs0));
        hm.put(name1, resource(name1, rs1));
        return snapshot(provider, hm);
    }


    public static MetricsSnapshot snapshot(Map<String, MetricsSnapshot.Resource> resources) {
        String name = resources.keySet().iterator().next();
        String sourceLabel = name.replaceAll("/.*", "");
        MetricsProvider provider = new MetricsProvider() {
            @Override public @Nonnull Set<String> getLabels() {
                return ImmutableSet.of(sourceLabel);
            }
        };
        return snapshot(provider, resources);
    }

    public static MetricsSnapshot snapshot(MetricsProvider provider, String name, ResourceStatus rs) {
        HashMap<String, MetricsSnapshot.Resource> hm = new HashMap<>();
        hm.put(name, resource(name, rs));
        return snapshot(provider, hm);
    }

    public static MetricsSnapshot snapshot(MetricsProvider provider, Map<String, MetricsSnapshot.Resource> resources) {
        String name = resources.keySet().iterator().next();
        String sourceLabel = name.replaceAll("/.*", "");
        return new MetricsSnapshot(provider, sourceLabel, resources);
    }

    private static @Nonnull MetricsSnapshot.Resource resource(String name, ResourceStatus status) {
        return new MetricsSnapshot.Resource(name, status, "Description of " + name);
    }
}
