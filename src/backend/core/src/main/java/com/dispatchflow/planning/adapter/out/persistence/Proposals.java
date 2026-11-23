package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.plan.ReoptimizationProposal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaProposalRepository}. A proposal has ten collections, so
 * {@link #findById} fetches one and a query each fetches the others into the same persistence
 * context, which keeps each query's rows to one collection's (ADR-126). The fetches run whatever
 * the proposal's load check read: loading whole must not depend on it, because Jakarta
 * Persistence calls touching a relationship in a lifecycle callback unportable.
 */
interface Proposals extends Repository<ReoptimizationProposal, UUID> {

    /**
     * Saves a proposal.
     *
     * @param proposal the proposal
     * @return the managed proposal
     */
    ReoptimizationProposal save(ReoptimizationProposal proposal);

    /**
     * Loads a proposal with its routes.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @EntityGraph(attributePaths = "routes")
    Optional<ReoptimizationProposal> findById(UUID id);

    /**
     * Fetches a proposal's stops into the proposal this transaction already holds, whether or not
     * its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.stops where p.id = :id")
    Optional<ReoptimizationProposal> fetchStops(UUID id);

    /**
     * Fetches a proposal's drops into the proposal this transaction already holds, whether or not
     * its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.dropped where p.id = :id")
    Optional<ReoptimizationProposal> fetchDropped(UUID id);

    /**
     * Fetches a proposal's excluded vehicles into the proposal this transaction already holds,
     * whether or not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.excluded where p.id = :id")
    Optional<ReoptimizationProposal> fetchExcluded(UUID id);

    /**
     * Fetches a proposal's withdrawn vehicles into the proposal this transaction already holds,
     * whether or not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.withdrawn where p.id = :id")
    Optional<ReoptimizationProposal> fetchWithdrawn(UUID id);

    /**
     * Fetches a proposal's late stops into the proposal this transaction already holds, whether or
     * not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.lateStops where p.id = :id")
    Optional<ReoptimizationProposal> fetchLateStops(UUID id);

    /**
     * Fetches a proposal's vehicles without a usable position into the proposal this transaction
     * already holds, whether or not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.withoutUsablePosition where p.id = :id")
    Optional<ReoptimizationProposal> fetchWithoutUsablePosition(UUID id);

    /**
     * Fetches the drivers of a proposal's driver changes into the proposal this transaction already
     * holds, whether or not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.changedDrivers where p.id = :id")
    Optional<ReoptimizationProposal> fetchChangedDrivers(UUID id);

    /**
     * Fetches the orders of a proposal's driver changes into the proposal this transaction already
     * holds, whether or not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.changedOrders where p.id = :id")
    Optional<ReoptimizationProposal> fetchChangedOrders(UUID id);

    /**
     * Fetches the orders whose goods need a hand-over into the proposal this transaction already
     * holds, whether or not its load check read them.
     *
     * @param id the proposal's UUID
     * @return the proposal, or empty
     */
    @Query("select p from ReoptimizationProposal p left join fetch p.needsGoodsHandOver where p.id = :id")
    Optional<ReoptimizationProposal> fetchNeedsGoodsHandOver(UUID id);
}
