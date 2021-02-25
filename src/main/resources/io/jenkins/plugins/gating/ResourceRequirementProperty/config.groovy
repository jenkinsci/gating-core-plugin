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

package io.jenkins.plugins.gating.ResourceRequirementProperty

import io.jenkins.plugins.gating.GatingMetrics
import io.jenkins.plugins.gating.MetricsSnapshot
import io.jenkins.plugins.gating.ResourceRequirementProperty

def f = namespace(lib.FormTagLib)
ResourceRequirementProperty rrp = (ResourceRequirementProperty) instance

f.optionalBlock(field: "declares_resources", inline: true, checked: rrp != null, title: "Resource gating") {
    def declaredResources = rrp == null ? [] : rrp.resources
    f.entry(field: "resources", title: "Required resources") {
        resources = GatingMetrics.get().statusOfAllResources
        def reportedResources = resources.keySet()

        // Some or all metrics can be temporarily absent. Insert all configured values to make sure we do not drop
        // those not backed by reported resources on submit.
        def offeredResources = reportedResources + declaredResources
        select(name: "resources", multiple: "multiple", size: Math.min(10, offeredResources.size())) {
            offeredResources.each { resource ->
                boolean selected = declaredResources.contains(resource)
                option(value: resource, selected: selected ? "selected" : null, title: resources.get(resource)?.description) {
                    text(resource)
                }
            }
        }
    }
}
