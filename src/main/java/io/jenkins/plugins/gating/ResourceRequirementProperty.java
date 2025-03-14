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
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job property to declare the build require certain resources to be operational before the build can start.
 */
public final class ResourceRequirementProperty extends JobProperty<Job<?, ?>> implements Serializable {

    @Serial
    private static final long serialVersionUID = -4060336631507729998L;
    private final @Nonnull List<String> resources;

    @DataBoundConstructor
    public ResourceRequirementProperty(@Nonnull List<String> resources) {
        this.resources = Collections.unmodifiableList(resources);
    }

    public @Nonnull List<String> getResources() {
        return resources;
    }

    /**
     * Evaluate availability and return run/no-run decision.
     *
     * @param availability Real world state of resources
     * @return null when satisfied, reasoning otherwise
     */
    public @CheckForNull ResourceBlockage evaluate(GatingMetrics availability) {
        ArrayList<String> missing = new ArrayList<>();

        Map<String, MetricsSnapshot.Resource> metrics = availability.getStatusOfAllResources();
        for (String resourceName : resources) {
            MetricsSnapshot.Resource resource = metrics.get(resourceName);
            ResourceStatus status = resource == null
                    ? ResourceStatus.Category.UNKNOWN
                    : resource.getStatus()
            ;

            if (status != ResourceStatus.Category.UP && status.getCategory() != ResourceStatus.Category.UP) {
                missing.add(String.format("%s is %s", resourceName, status));
            }
        }

        return missing.isEmpty()
                ? null // No unsatisfied resource - run
                : new ResourceBlockage(missing)
        ;
    }

    @Extension
    @Symbol("requireResources")
    public static final class Desc extends JobPropertyDescriptor {
        @Override
        public @Nonnull String getDisplayName() {
            return "Gating requirement";
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            // Do not create the property in case it is not activated
            if (formData.getBoolean("declares_resources")) {
                return super.newInstance(req, formData);
            }

            return null;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return ExtensionList.lookup(MetricsProvider.class).size() > 0;
        }
    }
}
