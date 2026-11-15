package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.GeocodeResult;
import com.dispatchflow.orders.domain.NormalizedAddress;

/**
 * The outside geocoding service, which turns an address into coordinates (FR-12, TS §6.4). GovMap
 * serves it, with Nominatim only if GovMap's terms fail (ADR-085). The adapter keeps the cache
 * that stops one address being looked up again, within the provider's terms (FR-13, NFR-17).
 *
 * <p>A use case calls it before its transaction opens (DL §6.3), so no transaction waits on HTTP.
 *
 * <p>Built in M7.
 */
public interface Geocoder {

    /**
     * Looks an address up.
     *
     * <p>Built in M7.
     *
     * @param address the normalized address (FR-398)
     * @return {@link GeocodeResult.Found} with the point, the match's quality and the distance to
     *     the nearest road (FR-63), or {@link GeocodeResult.NotFound}; never {@link
     *     GeocodeResult.GaveUp}, which only the geocoding job makes, after 24 hours of retries
     *     (FR-64)
     * @throws GeocoderUnavailable when the provider can't be reached or refuses the call, so the
     *     request stays in the outbox and is tried again (FR-64)
     */
    GeocodeResult lookUp(NormalizedAddress address);
}
