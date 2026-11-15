package com.dispatchflow.orders.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.Geocoder;
import com.dispatchflow.shared.application.Clock;
import java.time.Period;

/**
 * The nightly retention job's geocode cache step (NFR-259, ADR-075, ADR-085): it deletes every
 * cache entry no lookup has used for {@link #UNUSED_FOR}, counted back from the job's time in
 * Israel's calendar, through {@link Geocoder#forgetUnusedSince}. The customers keep the coordinates
 * they copied.
 *
 * <p>It records its own run through {@link AuditLog#succeeded}, naming no record, as Audit's own
 * purge does: so an Admin can see that the step ran, and when (NFR-93, FR-126). A cache entry has
 * no ID an audit record could name, only an address, which is personal data (ADR-110, NFR-94).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class ForgetUnusedGeocodes {

    /** How long a cache entry may go unused before it is deleted (NFR-259, ADR-075). */
    public static final Period UNUSED_FOR = Period.ofYears(1);

    private final Geocoder geocoder;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param geocoder the outside geocoding service, whose adapter keeps the cache
     * @param clock the time of the job, which the cutoff counts back from and the run is recorded
     *     at
     * @param auditLog where the run's own audit record is written
     */
    public ForgetUnusedGeocodes(Geocoder geocoder, Clock clock, AuditLog auditLog) {
        this.geocoder = geocoder;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deletes the cache entries unused for {@link #UNUSED_FOR}, and records the run.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many entries were deleted
     */
    public int forgetDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
