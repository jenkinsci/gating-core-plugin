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

import io.jenkins.plugins.gating.GatingMatrices
import io.jenkins.plugins.gating.ResourceRequirementProperty

def f = namespace(lib.FormTagLib)
ResourceRequirementProperty rrp = (ResourceRequirementProperty) instance

f.optionalBlock(field: "declares_resources", inline: true, checked: !rrp.resouces.empty, title: "Resource gating") {
    f.entry(field: "resources", title: "Required resources") {
        def availableResources = GatingMatrices.get().statusOfAllResources.keySet()
        select(name: "resources", multiple: "multiple", size: Math.min(10, availableResources.size())) {
            availableResources.each { resource ->
                f.option(value: resource, selected: rrp.resouces.contains(resource)) {
                    text(resource)
                }
            }
        }
    }
}
