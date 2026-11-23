package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.planning.domain.plan.ReoptimizationProposal;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps re-optimization proposals in {@code planning.reoptimization_proposal}, with each of their
 * lists in a table of its own.
 */
@Component
@Transactional
class JpaProposalRepository implements ProposalRepository {

    private final Proposals proposals;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param proposals the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaProposalRepository(Proposals proposals, VersionedSave versionedSave) {
        this.proposals = Objects.requireNonNull(proposals, "proposals");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    /**
     * {@inheritDoc}
     *
     * <p>It loads the proposal with its routes, then each other collection into the same instance.
     * Its load check may already have read them while the root loaded, but loading whole must not
     * depend on what the check happens to read: Jakarta Persistence calls touching a relationship
     * in a lifecycle callback unportable.
     */
    @Override
    public Optional<ReoptimizationProposal> get(ProposalId id) {
        UUID key = id.value();
        Optional<ReoptimizationProposal> proposal = proposals.findById(key);
        if (proposal.isPresent()) {
            proposals.fetchStops(key);
            proposals.fetchDropped(key);
            proposals.fetchExcluded(key);
            proposals.fetchWithdrawn(key);
            proposals.fetchLateStops(key);
            proposals.fetchWithoutUsablePosition(key);
            proposals.fetchChangedDrivers(key);
            proposals.fetchChangedOrders(key);
            proposals.fetchNeedsGoodsHandOver(key);
        }
        return proposal;
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the
     * proposal's key into {@code StaleVersion}.
     */
    @Override
    public void add(ReoptimizationProposal proposal) {
        versionedSave.save(proposal, proposals::save);
    }

    @Override
    public void save(ReoptimizationProposal proposal) {
        versionedSave.save(proposal, proposals::save);
    }
}
