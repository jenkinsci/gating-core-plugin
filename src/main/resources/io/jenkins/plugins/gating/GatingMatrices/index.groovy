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
package io.jenkins.plugins.gating.GatingMatrices

import hudson.model.Job
import io.jenkins.plugins.gating.GatingMatrices

def l = namespace(lib.LayoutTagLib)
GatingMatrices gating = (GatingMatrices) my

style("""
        #matrices th {
            text-align: left;
        }

        #matrices td.UP {
            background-color: #3fdf3f;
        }
        #matrices td.DEGRADED {
        background-color: #ffd485;
        }
        #matrices td.UNKNOWN {
            background-color: #ccc;
        }
        #matrices td.DOWN {
            background-color: #ff7171;
        }
""")

l.layout(permission: Job.CONFIGURE) {
    include(app, "sidepanel.jelly")
    l.header(title: gating.displayName)
    l.main_panel {
        h1(gating.displayName)

        gating.detectedConflicts.each {
            p(strong(it))
        }

        def matrices = gating.matrices
        if (matrices.isEmpty()) {
            p(strong("No matrices available. Either no sources were configured, or the data have not been received yet."))
        } else {
            matrices.each { name, snapshot ->
                h2(name)
                small(snapshot.created)
                table(class: "pane sortable bigtable", width: "100%", id: "matrices") {
                    tr {
                        th { text("Resource") }
                        th { text("Status") }
                    }
                    snapshot.statuses.each { resource, status ->
                        tr {
                            td { text(resource) }
                            td(class: status.getCategory().name()) {
                                strong(text(status))
                                def category = status.getCategory()
                                if (category != status) {
                                    small(" ($category)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
