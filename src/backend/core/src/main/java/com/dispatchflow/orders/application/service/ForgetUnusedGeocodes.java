package com.dispatchflow.orders.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.Geocoder;
import com.dispatchflow.shared.application.Clock;
import java.time.Period;

/**
 * The nightly retention job's geocode cache step (NFR-259, ADR-075, ADR-085): it deletes every
 * cache entry no lookup has used for {@link #UNUSED_FOR}, counted back from the job's time in
 * Israel's calendar, through {@link Geocoder#forgetUnusedSince}. The customers keep the coordinates
 * they copied. Like Audit's own purge, it writes no audit record: NFR-23 audits API calls, and a
 * cache entry has no ID an audit record could name, only an address (ADR-110).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121).
 */
public final class ForgetUnusedGeocodes {

    /** How long a cache entry may go unused before it is deleted (NFR-259, ADR-075). */
    public static final Period UNUSED_FOR = Period.ofYears(1);

    private final Geocoder geocoder;
    private final Clock clock;

    /**
     * Creates the job.
     *
     * @param geocoder the outside geocoding service, whose adapter keeps the cache
     * @param clock the time of the job
     */
    public ForgetUnusedGeocodes(Geocoder geocoder, Clock clock) {
        this.geocoder = geocoder;
        this.clock = clock;
    }

    /**
     * Deletes the cache entries unused for {@link #UNUSED_FOR}.
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
