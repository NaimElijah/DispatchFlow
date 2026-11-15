package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.ProposalPreviewView;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The re-optimization preview a dispatcher judges before accepting or declining it (FR-36, FR-103,
 * FR-334, BR-81). It is a dispatcher screen: the Driver role never sees a preview not yet accepted,
 * whatever the account's other roles (BR-41), which this class enforces once its milestone builds it.
 *
 * <p>Built in M9.
 */
public final class ProposalQueries {

    private final PlanningViews views;

    /**
     * Creates the queries.
     *
     * @param views Planning's read side
     */
    public ProposalQueries(PlanningViews views) {
        this.views = views;
    }

    /**
     * A proposal's preview.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher
     * @param proposal the proposal
     * @return what changes for each driver, the drops, the broken windows, the withdrawn vehicles, the
     *     vehicles without a usable position, the goods hand-overs and the change in the totals
     * @throws NotFound when no proposal has the ID
     */
    public ProposalPreviewView preview(Actor actor, ProposalId proposal) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
