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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import org.jfree.chart.renderer.category.BarRenderer3D;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Extension
public final class GatingMatrices implements RootAction {

    public static final @Nonnull String DELIM = "/";

    private @Nonnull final Object matricesLock = new Object();

    /**
     * Map of matrices source to matrices snapshot. All resource names provided are expected to be prefixed with the source
     * label making sure resource names does not collide across sources. Snapshot handles resource names in certain source
     * as keys of the map making sure they are unique within the source.
     *
     * Updates are only performed by adding/replacing/removing of values per given key.
     */
    @GuardedBy("matricesLock")
    private @Nonnull final Map<String, Snapshot> matricesMap = new HashMap<>();

    /**
     * Map of all resources.
     *
     * This is a cache to be invalidated when resource update arrives and populated when requested.
     */
    @GuardedBy("matricesLock")
    private @CheckForNull Map<String, ResourceStatus> resourceMap = null;

    public static @Nonnull GatingMatrices get() {
        return ExtensionList.lookupSingleton(GatingMatrices.class);
    }

    @Override
    public @Nonnull String getIconFileName() {
        return "notepad.png";
    }

    @Override
    public @Nonnull String getDisplayName() {
        return "Gating Matrices";
    }

    @Override
    public @Nonnull String getUrlName() {
        return "gating";
    }

    public @Nonnull Map<String, Snapshot> getMatrices() {
        synchronized (matricesLock) {
            return new HashMap<>(matricesMap);
        }
    }

    /**
     * Get all resources and their status. The collection is immutable.
     */
    public @Nonnull Map<String, ResourceStatus> getStatusOfAllResources() {
        synchronized (matricesLock) {
            if (resourceMap != null) return resourceMap;

            Map<String, ResourceStatus> statuses = new HashMap<>();
            for (Snapshot snapshot : matricesMap.values()) {
                // Names are guaranteed not to collide
                statuses.putAll(snapshot.statuses);
            }

            resourceMap = Collections.unmodifiableMap(statuses);
            return resourceMap;
        }
    }

    public void update(@Nonnull String sourceLabel, @Nonnull Snapshot snapshot) {
        if (sourceLabel == null || sourceLabel.isEmpty()) throw new IllegalArgumentException("Empty source label");

        for (String s : snapshot.statuses.keySet()) {
            if (!s.startsWith(sourceLabel + DELIM)) {
                throw new IllegalArgumentException(String.format(
                        "Resource name (%s) not prefixed with source label (%s%s)", s, sourceLabel, DELIM
                ));
            }
        }
        synchronized (matricesLock) {
            matricesMap.put(sourceLabel, snapshot);
            resourceMap = null; // Invalidate
        }
    }

    public static final class Snapshot {
        private final long created = System.currentTimeMillis();
        private final @Nonnull Map<String, ResourceStatus> statuses;

        public Snapshot(@Nonnull Map<String, ResourceStatus> statuses) {
            if (statuses.containsKey(null) || statuses.containsKey("")) {
                throw new IllegalArgumentException("Status map cannot contain empty resources");
            }

            if (statuses.containsValue(null)) {
                throw new IllegalArgumentException("Status map cannot contain null statuses");
            }

            this.statuses = Collections.unmodifiableMap(new HashMap<>(statuses));
        }

        public @Nonnull Date getCreated() {
            return new Date(created);
        }

        public @Nonnull Map<String, ResourceStatus> getStatuses() {
            return statuses;
        }

        public static Snapshot with(String name, ResourceStatus rs) {
            HashMap<String, ResourceStatus> hm = new HashMap<>();
            hm.put(name, rs);
            return new Snapshot(hm);
        }

        public Snapshot and(String name, ResourceStatus rs) {
            HashMap<String, ResourceStatus> hm = new HashMap<>(statuses);
            hm.put(name, rs);
            return new Snapshot(hm);
        }
    }
}
