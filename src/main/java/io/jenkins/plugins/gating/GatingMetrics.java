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
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Extension
public final class GatingMetrics implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(GatingMetrics.class.getName());
    private static final String REGEX = "^[a-zA-Z0-9_-]+$";
    private static final Predicate<String> SOURCE_LABEL_PREDICATE = Pattern.compile(REGEX).asPredicate();

    public static final @Nonnull String DELIM = "/";
    public static final Comparator<String> RESOURCE_ID_COMPARATOR = String::compareToIgnoreCase;

    private @Nonnull final Object metricsLock = new Object();

    /**
     * Map of metrics source to metrics snapshot. All resource names provided are expected to be prefixed with the source
     * label making sure resource names does not collide across sources. Snapshot handles resource names in certain source
     * as keys of the map making sure they are unique within the source.
     *
     * Updates are only performed by adding/replacing/removing of values per given key.
     */
    @GuardedBy("metricsLock")
    private final @Nonnull Map<String, MetricsSnapshot> metricsMap = new HashMap<>();

    /**
     * Map of all resources.
     *
     * This is a cache to be invalidated when resource update arrives and populated when requested.
     */
    @GuardedBy("metricsLock")
    private @CheckForNull Map<String, MetricsSnapshot.Resource> resourceMap = null;

    /**
     * Map of errors updating data.
     *
     * Errors does not remove latest reported metrics, but reported data should remove the latest error.
     */
    @GuardedBy("metricsLock")
    private final @Nonnull Map<String, MetricsSnapshot.Error> errorMap = new HashMap<>();

    public static @Nonnull GatingMetrics get() {
        return ExtensionList.lookupSingleton(GatingMetrics.class);
    }

    @Override
    public @Nonnull String getIconFileName() {
        return "notepad.png";
    }

    @Override
    public @Nonnull String getDisplayName() {
        return "Gating Metrics";
    }

    @Override
    public @Nonnull String getUrlName() {
        return "gating";
    }

    public @Nonnull Map<String, MetricsSnapshot> getMetrics() {
        synchronized (metricsLock) {
            return new HashMap<>(metricsMap);
        }
    }

    @Restricted(NoExternalUse.class)
    public @Nonnull Map<String, MetricsSnapshot.Error> getErrors() {
        synchronized (metricsLock) {
            if (errorMap.isEmpty()) return Collections.emptyMap();

            return new HashMap<>(errorMap);
        }
    }

    /**
     * Get all resources and their status. The collection is immutable.
     */
    public @Nonnull Map<String, MetricsSnapshot.Resource> getStatusOfAllResources() {
        synchronized (metricsLock) {
            if (resourceMap != null) return resourceMap;

            Map<String, MetricsSnapshot.Resource> statuses = new TreeMap<>(RESOURCE_ID_COMPARATOR);
            for (MetricsSnapshot snapshot : metricsMap.values()) {
                // Names are guaranteed not to collide
                statuses.putAll(snapshot.getStatuses());
            }

            resourceMap = Collections.unmodifiableMap(statuses);
            return resourceMap;
        }
    }

    @Restricted(NoExternalUse.class)
    public List<String> getDetectedConflicts() {
        ArrayList<String> conflictMessages = new ArrayList<>();

        Map<String, MetricsProvider> labelToProvider = new HashMap<>();
        for (MetricsProvider provider : ExtensionList.lookup(MetricsProvider.class)) {
            for (String label : provider.getLabels()) {
                FormValidation validation = validateLabel(label);
                if (validation.kind != FormValidation.Kind.OK) {
                    conflictMessages.add(validation.getMessage());
                }

                MetricsProvider existingProvider = labelToProvider.get(label);
                if (existingProvider != null) {
                    conflictMessages.add(labelConflictError(provider, existingProvider, label));
                } else {
                    labelToProvider.put(label, provider);
                }
            }
        }

        return conflictMessages;
    }

    public void update(@Nonnull MetricsSnapshot snapshot) {
        String sourceLabel = snapshot.getSourceLabel();
        LOGGER.fine("Received metrics update for source " + sourceLabel);

        synchronized (metricsLock) {
            if (!isMatchingProvider(sourceLabel, snapshot.getProvider())) return;

            metricsMap.put(sourceLabel, snapshot);
            errorMap.remove(sourceLabel); // Erase previous error
            resourceMap = null; // Invalidate cache
        }

        // TODO Only when something changed
        GatingStep.metricsUpdated();
    }

    public void reportError(MetricsSnapshot.Error error) {
        String sourceLabel = error.getSourceLabel();
        LOGGER.info("Received error for source " + sourceLabel);

        synchronized (metricsLock) {
            if (!isMatchingProvider(sourceLabel, error.getProvider())) return;

            // Track error. Do not remove latest known data, nor the cache.
            errorMap.put(sourceLabel, error);
        }
    }

    private boolean isMatchingProvider(String sourceLabel, MetricsProvider incomingProvider) {
        MetricsSnapshot oldData = metricsMap.get(sourceLabel);
        if (oldData != null && oldData.getProvider() != incomingProvider) {
            // Source label conflict - ignore all but first
            LOGGER.severe(labelConflictError(incomingProvider, oldData.getProvider(), sourceLabel));
            return false;
        }
        MetricsSnapshot.Error oldError = errorMap.get(sourceLabel);
        if (oldError != null && oldError.getProvider() != incomingProvider) {
            // Source label conflict - ignore all but first
            LOGGER.severe(labelConflictError(incomingProvider, oldError.getProvider(), sourceLabel));
            return false;
        }

        return true;
    }

    private String labelConflictError(MetricsProvider lhs, MetricsProvider rhs, String sourceLabel) {
        return String.format(
                "Providers %s and %s have a colliding sourceLabel %s. Ignoring metrics update.",
                getProviderDescription(lhs),
                getProviderDescription(rhs),
                sourceLabel
        );
    }

    private String getProviderDescription(MetricsProvider p) {
        Symbol s = p.getClass().getAnnotation(Symbol.class);
        return s == null ? p.toString() : s.value()[0];
    }

    public static FormValidation validateLabel(@CheckForNull String label) {
        boolean valid = label != null && SOURCE_LABEL_PREDICATE.test(label);
        if (valid) return FormValidation.ok();

        return FormValidation.error("Invalid source label name: " + label + ". Must match /" + REGEX + "/.");
    }
}
