package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.liveops.application.port.out.proof.ProofStore;
import com.dispatchflow.liveops.application.port.out.proof.ProofUpload;
import com.dispatchflow.liveops.domain.delivery.ProofId;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.NotFound;

/**
 * The proof-of-delivery screen: a dispatcher views a stop's proof (FR-105). The file is served only
 * through this authorized call, to the roles the permission matrix lets see it (NFR-38, ADR-027,
 * ADR-068).
 *
 * <p>Through the Driver role alone, an actor sees a proof only on their own route; an account that
 * is also a Dispatcher or an Admin sees every proof (BR-02). For such a Driver, {@link
 * LiveOpsViews#stopOfProof} gives the stop whose delivery names the proof, and Planning's {@link
 * PublishedRoutes#routeOf} that stop's route in force and its driver now; a proof on any other
 * route answers {@link NotFound}, the same as an unknown ID, so a driver learns nothing of another
 * route's proofs.
 *
 * <p>Built in M5. Rows: FR-105, NFR-38, BR-02.
 */
public final class ProofQueries {

    private final ProofStore proofs;
    private final LiveOpsViews views;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the queries.
     *
     * @param proofs where proof files are kept
     * @param views Live Operations' read port, for the stop a proof is on
     * @param publishedRoutes Planning's routes in force, for that stop's driver
     */
    public ProofQueries(ProofStore proofs, LiveOpsViews views, PublishedRoutes publishedRoutes) {
        this.proofs = proofs;
        this.views = views;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * One proof's file.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher or Admin viewing it, or a Driver viewing a proof on their own
     *     route
     * @param proof the proof's ID, as the stop's history names it
     * @return the stored image
     * @throws NotFound when no file is stored under the ID, or retention deleted it (NFR-184), or the
     *     actor views through the Driver role alone and the proof's stop is on no route in force
     *     that they drive (BR-02)
     */
    public ProofUpload proof(Actor actor, ProofId proof) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
