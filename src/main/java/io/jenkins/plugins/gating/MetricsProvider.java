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

import hudson.ExtensionPoint;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Provider abstraction user are supposed to implement.
 *
 * This must be a singleton so {@link GatingMetrics#update(MetricsSnapshot)} is going to recognise duplicates.
 * Ideally implemented as {@link jenkins.model.GlobalConfiguration}.
 */
public interface MetricsProvider extends ExtensionPoint {
    /**
     * Source label names this provider is configured to service.
     *
     * Individual providers must provide labels that are unique within Jenkins. Labels must ne non-empty alphanumeric strings.
     */
    @Nonnull Set<String> getLabels();
}
