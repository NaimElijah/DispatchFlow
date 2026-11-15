package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import java.util.Optional;

/**
 * The travel matrix each plan was solved with, stored with the plan, compressed, beside its list of
 * locations and that list's hash (BR-37, TS §6.5, ADR-052). An edit check, the publishing re-check
 * and a re-optimization reuse it, so they don't call OSRM again while it has every location they
 * need. A solve over the same locations reuses it too: the matrix is cached by its location list's
 * hash (ADR-052).
 *
 * <p>The list of locations holds customers' places, so retention drops it 90 days after the plan's
 * date, and keeps the matrix's numbers and the list's hash (NFR-257, NFR-260, ADR-121). The cache
 * keeps working through the hash: a caller that holds a list finds the matrix by the list's hash
 * ({@link #matching}), and the list it holds gives the rows their locations again.
 *
 * <p>Built in M3 and M13, method by method.
 */
public interface StoredMatrices {

    /**
     * Stores the matrix a solve job's request was built with, in {@code RunSolveJob}'s success
     * transaction, so a failed job stores none (ADR-052, ADR-121). That is a daily job's matrix, or
     * a re-optimization's when the stored one lacked a location and the request was built over a
     * new one. It replaces the plan's earlier matrix, which the job's own stored input still
     * records (DL §9.7). A matrix over the same list as the stored one, by the list's hash, leaves
     * it as it is.
     *
     * <p>Built in M3.
     *
     * @param plan the plan
     * @param job the solve job that built it
     * @param matrix the matrix
     */
    void store(PlanId plan, JobId job, TravelMatrix matrix);

    /**
     * The matrix the plan's latest stored solve was built with, with its list of locations, for an
     * edit check, the publishing re-check and a re-optimization to find which locations it has.
     *
     * <p>Built in M3.
     *
     * @param plan the plan
     * @return the matrix, or empty when the plan was never solved or retention dropped its list of
     *     locations
     */
    Optional<TravelMatrix> of(PlanId plan);

    /**
     * The plan's stored matrix when it was built over exactly these locations, in this order, found
     * by the list's hash (ADR-052). It reads the numbers and the hash only, so it answers after
     * retention dropped the stored list (ADR-121).
     *
     * <p>Built in M3.
     *
     * @param plan the plan
     * @param locations the locations a new request needs, each once, in the order its matrix
     *     follows
     * @return the matrix over {@code locations}, or empty when the plan was never solved or its
     *     stored matrix is over another list
     */
    Optional<TravelMatrix> matching(PlanId plan, List<GeoPoint> locations);

    /**
     * Drops the list of locations of every plan dated before a cutoff, and keeps its matrix's
     * numbers and its list's hash, in the caller's transaction (NFR-257, NFR-260, ADR-121). A plan
     * whose list is already dropped is left as it is, so a second run drops nothing.
     *
     * <p>Built in M13.
     *
     * @param cutoff the first date whose plans keep their lists: today less 90 Israel calendar days
     *     (ADR-088)
     * @return how many plans' lists were dropped
     */
    int dropLocationsDatedBefore(DeliveryDate cutoff);
}
