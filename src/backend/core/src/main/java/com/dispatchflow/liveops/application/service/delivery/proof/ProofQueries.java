package com.dispatchflow.liveops.application.service.delivery.proof;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.liveops.application.port.out.proof.ProofStore;
import com.dispatchflow.liveops.application.port.out.proof.ProofUpload;
import com.dispatchflow.liveops.domain.proof.ProofId;
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
 * route's proofs. {@link ProofQueriesChange} makes that check in a read-only transaction; after it,
 * the file is read from {@link ProofStore}, on disk, outside any transaction.
 *
 * <p>To Be Implemented. Rows: FR-105, NFR-38, BR-02.
 */
public final class ProofQueries {

    private final ProofStore proofs;
    private final ProofQueriesChange change;

    /**
     * Creates the queries.
     *
     * @param proofs where proof files are kept
     * @param change the check that the actor may see the proof
     */
    public ProofQueries(ProofStore proofs, ProofQueriesChange change) {
        this.proofs = proofs;
        this.change = change;
    }

    /**
     * One proof's file.
     *
     * <p>To Be Implemented.
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
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
