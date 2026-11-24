package com.dispatchflow.liveops.application.service.delivery.proof;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.liveops.domain.proof.ProofId;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transaction of {@link ProofQueries} (FR-105, BR-02): it checks that the actor may
 * see the proof. A Dispatcher or an Admin sees every proof; through the Driver role alone, {@link
 * LiveOpsViews#stopOfProof} gives the proof's stop, and Planning's {@link PublishedRoutes#routeOf}
 * that stop's route in force and its driver now.
 *
 * <p>Built in M5.
 */
@Transactional(readOnly = true)
public class ProofQueriesChange {

    private final LiveOpsViews views;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the check.
     *
     * @param views Live Operations' read port, for the stop a proof is on
     * @param publishedRoutes Planning's routes in force, for that stop's driver
     */
    public ProofQueriesChange(LiveOpsViews views, PublishedRoutes publishedRoutes) {
        this.views = views;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * Checks the actor may see the proof, and returns if so.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher or Admin viewing it, or a Driver viewing a proof on their own
     *     route
     * @param proof the proof's ID, as the stop's history names it
     * @throws NotFound when the actor views through the Driver role alone and the proof's stop is on
     *     no route in force that they drive, or no delivery names the proof (BR-02)
     */
    public void requireVisible(Actor actor, ProofId proof) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
