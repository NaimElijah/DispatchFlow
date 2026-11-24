package com.dispatchflow.planning.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.StoredMatrices;
import com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three transactions of {@link EraseExpiredLocations}, each of which ends one kind of copy and
 * writes its audit record with the count and no personal data (NFR-184, NFR-257, NFR-260, ADR-110,
 * ADR-121, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class EraseExpiredLocationsChange {

    private final StoredMatrices storedMatrices;
    private final SolveJobRepository jobs;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param storedMatrices each plan's stored matrix, whose list of locations is dropped
     * @param jobs where solve jobs are kept, with their inputs
     * @param clock the server's time, for {@code today} and {@code now}
     * @param auditLog where each step's audit record is written
     */
    public EraseExpiredLocationsChange(
            StoredMatrices storedMatrices, SolveJobRepository jobs, Clock clock, AuditLog auditLog) {
        this.storedMatrices = storedMatrices;
        this.jobs = jobs;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Drops the stored list of locations of every plan dated 90 Israel calendar days ago or earlier
     * (NFR-257).
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many plans' lists were dropped
     */
    public int dropLocations(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Blanks the coordinates in the inputs of every solve job of a plan dated 90 Israel calendar days
     * ago or earlier (NFR-260).
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many jobs' inputs were blanked
     */
    public int blankCoordinates(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Blanks every position received 7 Israel calendar days ago or earlier, and its position row, in
     * the re-optimization inputs that hold it (NFR-184).
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many jobs' inputs were blanked
     */
    public int blankPositions(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
