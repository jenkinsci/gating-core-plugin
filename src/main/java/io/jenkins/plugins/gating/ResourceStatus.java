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

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * Immutable status of monitored resource.
 *
 * This is either {@link Category} or a service-specific status-set typically implemented as enum. This permit
 * users to state requirements based on general categories as well as particular source-specific statuses.
 */
public interface ResourceStatus {
    /**
     * Get Category of the resource.
     */
    @Nonnull Category getCategory();

    /**
     * Category of the resource status.
     *
     * Different monitoring services have a different resource states, this provides a reusable abstraction needed by Jenkins gating.
     */
    enum Category implements ResourceStatus {
        /**
         * The resource is fully operational.
         */
        UP,

        /**
         * The resource is partially operational.
         */
        DEGRADED,

        /**
         * The resource status is unknown.
         */
        UNKNOWN,

        /**
         * The resource is not operational.
         */
        DOWN;

        @Override
        public @Nonnull Category getCategory() {
            return this;
        }
    }
}
