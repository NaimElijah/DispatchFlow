package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.ProofStore;
import com.dispatchflow.liveops.application.port.out.ProofUpload;
import com.dispatchflow.liveops.domain.delivery.ProofId;
import com.dispatchflow.shared.application.NotFound;

/**
 * The proof-of-delivery screen: a dispatcher views a stop's proof (FR-105). The file is served only
 * through this authorized call, to the roles the permission matrix lets see it (NFR-38, ADR-027,
 * ADR-068).
 *
 * <p>Built in M5. Rows: FR-105, NFR-38.
 */
public final class ProofQueries {

    private final ProofStore proofs;

    /**
     * Creates the queries.
     *
     * @param proofs where proof files are kept
     */
    public ProofQueries(ProofStore proofs) {
        this.proofs = proofs;
    }

    /**
     * One proof's file.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher or Admin viewing it
     * @param proof the proof's ID, as the stop's history names it
     * @return the stored image
     * @throws NotFound when no file is stored under the ID, or retention deleted it (NFR-184)
     */
    public ProofUpload proof(Actor actor, ProofId proof) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
