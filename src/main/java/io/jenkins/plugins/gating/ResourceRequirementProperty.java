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
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job property to declare the build require certain resources to be operational before the build can start.
 */
public final class ResourceRequirementProperty extends JobProperty<Job<?, ?>> {

    private final @Nonnull List<String> resources;

    @DataBoundConstructor
    public ResourceRequirementProperty(@Nonnull List<String> resources) {
        this.resources = Collections.unmodifiableList(resources);
    }

    public @Nonnull List<String> getResouces() {
        return resources;
    }

    /**
     * Evaluate availability and return run/no-run decision.
     *
     * @param availability Real world state of resources
     * @return null when satisfied, reasoning otherwise
     */
    public @CheckForNull ResourceBlockage evaluate(GatingMatrices availability) {
        ArrayList<String> missing = new ArrayList<>();

        Map<String, ResourceStatus> metrics = availability.getStatusOfAllResources();
        for (String res : resources) {
            ResourceStatus actual = metrics.get(res);
            if (actual.getCategory() != ResourceStatus.Category.UP) {
                missing.add(String.format("%s is %s", res, actual));
            }
        }

        return missing.isEmpty()
                ? null // No unsatisfied resource - run
                : new ResourceBlockage(missing)
        ;
    }

    @Extension
    public static final class Desc extends JobPropertyDescriptor {
        @Override
        public @Nonnull String getDisplayName() {
            return "Gating requirement";
        }
    }
}
