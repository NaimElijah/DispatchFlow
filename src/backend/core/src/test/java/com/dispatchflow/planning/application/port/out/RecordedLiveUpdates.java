package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import java.util.ArrayList;
import java.util.List;

/** {@link LiveUpdates} that sends nothing and keeps each send, in order, for a test to read. */
public final class RecordedLiveUpdates implements LiveUpdates {

    private final List<Sent> sent = new ArrayList<>();

    /**
     * What was sent.
     *
     * @return the sends, in the order made
     */
    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    @Override
    public void jobChanged(JobId job, PlanId plan) {
        sent.add(new JobChanged(job, plan));
    }

    @Override
    public void planChanged(PlanId plan) {
        sent.add(new PlanChanged(plan));
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void proposalReady(ProposalId proposal, PlanId plan) {
        throw new UnsupportedOperationException("no test records a proposal's send yet");
    }

    /** One send. */
    public sealed interface Sent {}

    /**
     * A {@link LiveUpdates#jobChanged} send.
     *
     * @param job the job
     * @param plan the plan it solves
     */
    public record JobChanged(JobId job, PlanId plan) implements Sent {}

    /**
     * A {@link LiveUpdates#planChanged} send.
     *
     * @param plan the plan
     */
    public record PlanChanged(PlanId plan) implements Sent {}
}
