package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.NormalizedAddress;
import java.time.Instant;
import java.util.Optional;

/**
 * The outside geocoding service, which turns an address into coordinates (FR-12, TS §6.4). GovMap
 * serves it, with Nominatim only if GovMap's terms fail (ADR-085). The adapter keeps the cache
 * that stops one address being looked up again, within the provider's terms (FR-13, NFR-17), and
 * records each entry's last use, so an entry unused for a year can be deleted (NFR-259, ADR-075).
 *
 * <p>A use case calls it before its transaction opens (DL §6.3), so no transaction waits on HTTP.
 * It answers the point and the match's quality only; the distance to the nearest road is {@link
 * RoadSnap}'s.
 *
 * <p>Built in M7, and in M13 for the cache's retention.
 */
public interface Geocoder {

    /**
     * Looks an address up, from the cache when it holds the address.
     *
     * <p>Built in M7.
     *
     * @param address the normalized address (FR-398)
     * @return the point and the match's quality, or empty when the provider found no match (FR-63,
     *     FR-218)
     * @throws GeocoderUnavailable when the provider can't be reached or refuses the call, so the
     *     request stays in the outbox and is tried again (FR-64)
     */
    Optional<GeocodeMatch> lookUp(NormalizedAddress address);

    /**
     * Deletes every cache entry that no lookup has used since an instant (NFR-259, ADR-075). The
     * customers keep the coordinates they copied (ADR-085).
     *
     * <p>Built in M13.
     *
     * @param cutoff the instant an entry's last use must predate to be deleted
     * @return how many entries were deleted, 0 or more
     */
    int forgetUnusedSince(Instant cutoff);
}
