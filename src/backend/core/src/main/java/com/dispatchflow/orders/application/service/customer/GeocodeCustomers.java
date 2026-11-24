package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.application.port.out.Geocoder;
import com.dispatchflow.orders.application.port.out.GeocoderUnavailable;
import com.dispatchflow.orders.application.port.out.customer.RoadSnap;
import com.dispatchflow.orders.application.port.out.customer.RoadSnapUnavailable;
import com.dispatchflow.shared.application.Clock;

/**
 * The geocoding job, which works the outbox (FR-12, FR-13, FR-63, FR-64, NFR-135, DL §12): it
 * claims the due requests, at most what is left of the day's {@value
 * GeocodeRequests#DAILY_LOOKUPS} lookups (NFR-135), and calls the {@link Geocoder} for each outside
 * any transaction (DL §6.3). For a point found, it then asks {@link RoadSnap} how far the point
 * lies from the nearest road, still before any transaction (ADR-085), and builds {@code
 * GeocodeResult.Found} from both answers; no match becomes {@code GeocodeResult.NotFound}. Then,
 * one customer per transaction, {@code Customer.recordGeocode} records the answer with the service
 * area and flags the doubtful ones (FR-63), and the request is done. A {@link GeocoderUnavailable}
 * or a {@link RoadSnapUnavailable} leaves the request for the next try, 15 minutes later; after 24
 * hours the job records {@code GeocodeResult.GaveUp}, which leaves the customer flagged for a
 * dispatcher to place its location by hand (FR-64). Past the day's cap the requests wait for the
 * next day, and the dispatcher is told through {@link CustomerQueries#geocodingBacklog} (NFR-135).
 *
 * <p>The {@link Geocoder} and the {@link RoadSnap} are outside calls, so this class opens no
 * transaction: {@link GeocodeCustomersChange} claims the due requests in one, and records each
 * customer's answer in one of its own (ADR-129).
 *
 * <p>Built in M7.
 */
public final class GeocodeCustomers {

    private final Geocoder geocoder;
    private final RoadSnap roadSnap;
    private final Clock clock;
    private final GeocodeCustomersChange change;

    /**
     * Creates the job.
     *
     * @param geocoder the outside geocoding service
     * @param roadSnap the road network's distance from a found point to the nearest road
     * @param clock the time of the job, and the Israel day the cap counts
     * @param change the transactions that claim the due requests and record each answer
     */
    public GeocodeCustomers(Geocoder geocoder, RoadSnap roadSnap, Clock clock, GeocodeCustomersChange change) {
        this.geocoder = geocoder;
        this.roadSnap = roadSnap;
        this.clock = clock;
        this.change = change;
    }

    /**
     * Works the due requests.
     *
     * <p>Built in M7.
     *
     * @param call the job's server step
     * @return how many requests were answered or given up on
     */
    public int geocodeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M7");
    }
}
