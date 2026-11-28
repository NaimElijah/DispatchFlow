package com.dispatchflow.planning.adapter.out.sse;

import com.dispatchflow.StandInOnly;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import org.springframework.stereotype.Component;

/**
 * Stands in for {@link LiveUpdates} until #193's adapter sends over the event stream and replaces
 * it, so the use cases that tell the open screens run before then (ADR-140 decision 10). Each send
 * does nothing.
 *
 * <p>Sending nothing loses nothing yet: no stream exists, so no screen is listening. It exists only
 * where the stand-in session does, exactly {@code dev} or exactly {@code test} ({@link
 * StandInOnly}), so a deployment that still lacks the real adapter fails at startup instead of
 * dropping its messages.
 */
@Component
@StandInOnly
final class LiveUpdatesStandIn implements LiveUpdates {

    @Override
    public void jobChanged(JobId job, PlanId plan) {
        // No stream exists to send on until #193 builds it.
    }

    @Override
    public void planChanged(PlanId plan) {
        // No stream exists to send on until #193 builds it.
    }

    @Override
    public void proposalReady(ProposalId proposal, PlanId plan) {
        // No stream exists to send on until #193 builds it.
    }
}
