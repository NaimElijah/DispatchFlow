package com.dispatchflow.planning.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.StoredMatrices;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's Planning step: Planning keeps copies of customers' coordinates and of
 * vehicles' positions, and this step ends them on the same deadlines as the originals (NFR-184,
 * NFR-257, NFR-260, ADR-121).
 *
 * <ul>
 *   <li>90 Israel calendar days after a plan's date (ADR-088), it drops the plan's stored list of
 *       locations through {@link StoredMatrices#dropLocationsDatedBefore}, keeping the matrix's
 *       numbers and the list's hash for the cache, and blanks the coordinates in its solve jobs'
 *       inputs through {@link SolveJobRepository#blankCoordinatesDatedBefore} (NFR-257, NFR-260).
 *   <li>7 Israel calendar days after a position was received, it blanks it and its position row in
 *       the re-optimization inputs that hold it, through {@link
 *       SolveJobRepository#blankPositionsReceivedBefore} (NFR-184).
 * </ul>
 *
 * <p>Each of the three steps runs in a transaction of its own and writes its audit record there,
 * with the count and no personal data (ADR-110). A step already done finds nothing, so a run
 * repeated after a failure is harmless. The scheduler runs it with the other retention steps at
 * 03:00 {@code Asia/Jerusalem} (DL §10.3).
 *
 * <p><b>Domain gap.</b> Planning's domain holds no retention periods: M13 adds them, as Live
 * Operations' {@code RetentionPeriods} holds its own, so the cutoffs come from the domain and not
 * from this step.
 *
 * <p>It lives in the service root because it changes two aggregates' records, the plans' stored
 * matrices and the solve jobs.
 *
 * <p>Built in M13.
 */
public final class EraseExpiredLocations {

    private final StoredMatrices storedMatrices;
    private final SolveJobRepository jobs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param storedMatrices each plan's stored matrix, whose list of locations is dropped
     * @param jobs where solve jobs are kept, with their inputs
     * @param clock the server's time, for {@code today} and {@code now}
     * @param auditLog where each step's audit record is written
     */
    public EraseExpiredLocations(
            StoredMatrices storedMatrices, SolveJobRepository jobs, Clock clock, AuditLog auditLog) {
        this.storedMatrices = storedMatrices;
        this.jobs = jobs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Drops and blanks what is due.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many plans' lists were dropped and jobs' inputs blanked, together
     */
    public int erase(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
