package com.dispatchflow.planning.application.port.out.proposal;

import com.dispatchflow.planning.domain.plan.ReoptimizationProposal;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where re-optimization proposals are kept: written once by the job that computed them, and changed
 * only to be declined (DL §3.6, §4.4, FR-36, BR-26). The persistence adapter loads one through {@code
 * ReoptimizationProposal.restore}.
 *
 * <p>Built in M9.
 */
public interface ProposalRepository {

    /**
     * One proposal.
     *
     * <p>Built in M9.
     *
     * @param id the proposal
     * @return the proposal, or empty when no proposal has the ID
     */
    Optional<ReoptimizationProposal> get(ProposalId id);

    /**
     * Adds the proposal a succeeded re-optimization created, in the job's transaction (DL §6.1).
     *
     * <p>Built in M9.
     *
     * @param proposal the proposal, at {@code Version.NEW}
     */
    void add(ReoptimizationProposal proposal);

    /**
     * Saves a declined proposal with its version check, in the caller's transaction (BR-76).
     *
     * <p>Built in M9.
     *
     * @param proposal the proposal, at the version it was loaded at
     * @throws StaleVersion when the proposal changed since it was loaded
     */
    void save(ReoptimizationProposal proposal);
}
