package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrdersTestData.CUSTOMER;
import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * The one builder of {@link Customer}s for tests: {@link #registered()} goes through {@link
 * Customer#register} alone, and {@link #build()} reaches the state the builder describes through the
 * factory and the commands, then saves it (ADR-127 decision 2).
 *
 * <p>The builder describes the customer it wants; {@link #build} replays the commands that lead
 * there and fails when they reach another state, so a test never starts from a state no command
 * reaches, such as an exact match flagged as partial. {@link #description} is the state described.
 */
final class CustomerBuilder {

    static final Address ADDRESS = Address.of("רח' הרצל 5, תל אביב");
    static final ContactName NAME = ContactName.of("משה כהן");
    static final PhoneNumber PHONE = PhoneNumber.parse("03-123-4567");
    static final DriverNote NOTES = DriverNote.of("הכניסה מהחצר").orElseThrow();

    /** 1 January 2025 at 10:00 in Israel, before every other time these tests use. */
    static final Instant CREATED = Instant.parse("2025-01-01T08:00:00Z");

    /** A square around central Tel Aviv: longitudes 34.70 to 34.90, latitudes 32.00 to 32.20. */
    static final ServiceArea AREA = ServiceArea.of(new GeometryFactory().createPolygon(new Coordinate[] {
        new Coordinate(34.70, 32.00),
        new Coordinate(34.90, 32.00),
        new Coordinate(34.90, 32.20),
        new Coordinate(34.70, 32.20),
        new Coordinate(34.70, 32.00)
    }));

    static final GeoPoint INSIDE = GeoPoint.of(32.08, 34.78);
    static final GeoPoint ALSO_INSIDE = GeoPoint.of(32.10, 34.80);
    static final GeoPoint OUTSIDE = GeoPoint.of(31.25, 34.79);

    /** Metres from a road past {@link Customer#FAR_FROM_ROAD_METRES}, for a geocode flagged far from one. */
    private static final double FAR_FROM_A_ROAD = Customer.FAR_FROM_ROAD_METRES + 1;

    private Instant createdAt = CREATED;
    private boolean createdByImport;
    private Location location = new Location.Missing();
    private Set<AddressFlag> geocodeFlags = EnumSet.noneOf(AddressFlag.class);
    private @Nullable Instant retiredAt;
    private @Nullable Instant anonymizedAt;

    private CustomerBuilder() {}

    static CustomerBuilder aCustomer() {
        return new CustomerBuilder();
    }

    CustomerBuilder createdAt(Instant at) {
        this.createdAt = at;
        return this;
    }

    /** Created by an import row (FR-311), so it has no note and carries the location-check flag. */
    CustomerBuilder createdByImport() {
        this.createdByImport = true;
        return this;
    }

    /**
     * Geocoded at a point with a match quality. The flags are those the answer gives, which {@link
     * #build} checks: the quality's, outside the area, far from a road, and the import's.
     */
    CustomerBuilder geocodedAt(GeoPoint point, MatchQuality quality, AddressFlag... withFlags) {
        this.location = new Location.Geocoded(point, quality);
        this.geocodeFlags = EnumSet.noneOf(AddressFlag.class);
        this.geocodeFlags.addAll(List.of(withFlags));
        this.createdByImport |= geocodeFlags.contains(AddressFlag.CREATED_BY_IMPORT);
        return this;
    }

    CustomerBuilder geocodedAt(GeoPoint point, AddressFlag... withFlags) {
        return geocodedAt(point, MatchQuality.EXACT, withFlags);
    }

    CustomerBuilder placedByHandAt(GeoPoint point) {
        this.location = new Location.HandPlaced(point);
        this.geocodeFlags = EnumSet.noneOf(AddressFlag.class);
        return this;
    }

    CustomerBuilder retiredAt(Instant at) {
        this.retiredAt = at;
        return this;
    }

    /** Anonymized at a time its retention is over, from its retirement or from its creation. */
    CustomerBuilder anonymizedAt(Instant at) {
        this.anonymizedAt = at;
        return this;
    }

    /**
     * Registers the customer a dispatcher created, and does nothing more.
     *
     * @return the customer, never saved
     */
    Customer registered() {
        return Customer.register(CUSTOMER, NAME, PHONE, ADDRESS, Optional.of(NOTES), DEFAULTS, createdAt);
    }

    /**
     * The customer this builder describes.
     *
     * @return the description {@link #build}'s customer matches
     */
    CustomerDescription description() {
        if (anonymizedAt != null) {
            return new CustomerDescription(
                    CUSTOMER,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    new Location.Missing(),
                    Set.of(),
                    DEFAULTS,
                    new Customer.State.Anonymized(anonymizedAt),
                    createdAt);
        }
        EnumSet<AddressFlag> flags = EnumSet.noneOf(AddressFlag.class);
        if (!(location instanceof Location.HandPlaced)) {
            flags.addAll(geocodeFlags);
            if (createdByImport) {
                flags.add(AddressFlag.CREATED_BY_IMPORT);
            }
        }
        return new CustomerDescription(
                CUSTOMER,
                Optional.of(NAME),
                Optional.of(PHONE),
                Optional.of(ADDRESS),
                createdByImport ? Optional.empty() : Optional.of(NOTES),
                location,
                Collections.unmodifiableSet(flags),
                DEFAULTS,
                retiredAt == null ? new Customer.State.Active() : new Customer.State.Retired(retiredAt),
                createdAt);
    }

    /**
     * Registers the customer, records its geocode or places its location, retires and anonymizes it
     * as described, and saves it.
     *
     * @return the customer, at version 0 with no pending event
     * @throws AssertionError when the commands reach another state than the builder describes
     */
    Customer build() {
        Customer customer = createdByImport
                ? Customer.registerFromImport(CUSTOMER, NAME, PHONE, ADDRESS, DEFAULTS, createdAt)
                : registered();
        switch (location) {
            case Location.Missing missing -> {}
            case Location.Geocoded geocoded -> {
                double metresToRoad = geocodeFlags.contains(AddressFlag.FAR_FROM_ROAD) ? FAR_FROM_A_ROAD : 0;
                customer.recordGeocode(
                        new GeocodeResult.Found(
                                NormalizedAddress.of(ADDRESS), geocoded.at(), geocoded.quality(), metresToRoad),
                        AREA);
            }
            case Location.HandPlaced handPlaced -> customer.placeLocation(handPlaced.at(), AREA);
        }
        if (retiredAt != null) {
            customer.retire(InUse.NONE, retiredAt);
        }
        if (anonymizedAt != null) {
            customer.anonymize(Optional.empty(), anonymizedAt);
        }
        if (!CustomerDescription.of(customer).equals(description())) {
            throw new AssertionError(
                    "the commands reached " + CustomerDescription.of(customer) + ", not " + description());
        }
        return asSaved(customer);
    }
}
