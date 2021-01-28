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

import hudson.Util;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable snapshot of resources reported by a single provider.
 */
public final class MatricesSnapshot {
    private final long created = System.currentTimeMillis();

    private final @Nonnull Map<String, Resource> statuses;
    private final @Nonnull MatricesProvider provider;
    private final @Nonnull String sourceLabel;

    public MatricesSnapshot(
            @Nonnull MatricesProvider provider,
            @Nonnull String sourceLabel,
            @Nonnull Map<String, Resource> statuses
    ) {
        if (sourceLabel == null || sourceLabel.isEmpty()) throw new IllegalArgumentException("Empty source label");
        this.provider = provider;
        this.sourceLabel = sourceLabel;

        if (statuses.containsKey(null) || statuses.containsKey("")) {
            throw new IllegalArgumentException("Status map cannot contain empty resources");
        }

        if (statuses.containsValue(null)) {
            throw new IllegalArgumentException("Status map cannot contain null statuses");
        }

        statuses.forEach((k ,v)-> {
            if (!k.startsWith(sourceLabel + GatingMatrices.DELIM)) {
                throw new IllegalArgumentException(String.format(
                        "Resource name (%s) not prefixed with source label (%s%s)", k, sourceLabel, GatingMatrices.DELIM
                ));
            }
            if (!Objects.equals(k, v.name)) {
                throw new IllegalArgumentException(String.format(
                        "Resource name (%s) have incorrect key (%s)", v.name, k
                ));
            }
        });

        TreeMap<String, Resource> out = new TreeMap<>(GatingMatrices.RESOURCE_ID_COMPARATOR);
        out.putAll(statuses);
        this.statuses = Collections.unmodifiableMap(out);
    }

    public @Nonnull Date getCreated() {
        return new Date(created);
    }

    public @Nonnull Map<String, Resource> getStatuses() {
        return statuses;
    }

    public @Nonnull MatricesProvider getProvider() {
        return provider;
    }

    public @Nonnull String getSourceLabel() {
        return sourceLabel;
    }

    public static final class Resource {
        private final @Nonnull String name;
        private final @Nonnull ResourceStatus status;
        private final @CheckForNull String description;

        public Resource(@Nonnull String name, @Nonnull ResourceStatus status, @CheckForNull String description) {
            this.name = name;
            this.status = status;
            this.description = Util.fixEmptyAndTrim(description);
        }

        public Resource(@Nonnull String name, @Nonnull ResourceStatus status) {
            this.name = name;
            this.status = status;
            this.description = null;
        }

        public @Nonnull String getName() {
            return name;
        }

        public @Nonnull ResourceStatus getStatus() {
            return status;
        }

        public @CheckForNull String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Resource resource = (Resource) o;
            return name.equals(resource.name) && status == resource.status && Objects.equals(description, resource.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, status, description);
        }

        @Override
        public String toString() {
            return String.format("Resource{name='%s', status=%s, description='%s'}", name, status, description);
        }
    }
}
