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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class GatingStep extends Step implements Serializable {
    private static final long serialVersionUID = -4244024221933297123L;
    private static final Logger LOGGER = Logger.getLogger(GatingStep.class.getName());

    // List of execution to be probed when new metrics arrives
    private static final List<Execution> blockedExecutions = new ArrayList<>();

    private final ResourceRequirementProperty requiredResources;

    @DataBoundConstructor
    public GatingStep(List<String> resources) {
        if (resources == null) throw new IllegalArgumentException("resources == null");

        requiredResources = new ResourceRequirementProperty(resources);
    }

    /*package*/ static void metricsUpdated() {
        ArrayList<Execution> executions;
        synchronized (blockedExecutions) {
            executions = new ArrayList<>(blockedExecutions);
        }

        for (Execution blockedExecution : executions) {
            try {
                blockedExecution.recheck();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    private static final class Execution extends StepExecution {
        private static final long serialVersionUID = -8240169797779406466L;

        private final GatingStep gatingStep;
        private String displayName;

        public Execution(StepContext context, GatingStep gatingStep) {
            super(context);
            this.gatingStep = gatingStep;
        }

        @Override
        public boolean start() throws Exception {
            displayName = getContext().get(Run.class).getFullDisplayName();
            ResourceBlockage blocked = gatingStep.requiredResources.evaluate(GatingMetrics.get());
            if (blocked == null) {
                LOGGER.finer("Running " + displayName + " right away");
                resumeToRunBody();
                return false; // This does not count as synchronous completion
            }
            LOGGER.fine("Starting to block " + displayName);
            reportBlockage(blocked);
            synchronized (blockedExecutions) {
                blockedExecutions.add(this);
            }
            return false;
        }

        @Override
        public void onResume() {
            LOGGER.info("Resuming blocked requireResources step for " + displayName);
            synchronized (blockedExecutions) {
                blockedExecutions.add(this);
            }
        }

        @Override
        public void stop(@Nonnull Throwable cause) {
            synchronized (blockedExecutions) {
                blockedExecutions.remove(this);
            }
            getContext().onFailure(cause);
        }

        public void recheck() throws InterruptedException, IOException {
            ResourceBlockage blocked = gatingStep.requiredResources.evaluate(GatingMetrics.get());
            if (blocked == null) {
                LOGGER.info("Unblocking requireResources for " + displayName);
                synchronized (blockedExecutions) {
                    resumeToRunBody();
                    blockedExecutions.remove(this);
                }
            } else {
                // TODO can take hours, better avoid updating it all the time
                reportBlockage(blocked);
            }
        }

        private void resumeToRunBody() {
            getContext().newBodyInvoker().start();
            getContext().onSuccess(null);
        }

        private void reportBlockage(ResourceBlockage blocked) throws IOException, InterruptedException {
            getContext().get(TaskListener.class).getLogger().println(blocked.getShortDescription());
        }
    }

    @Extension
    public static final class Descriptor extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "requireResources";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
