package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrdersTestData.CUSTOMER;
import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;

import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * The one builder of {@link Customer}s for tests: {@link #registered()} goes through {@link
 * Customer#register}, and {@link #restored()} through {@link Customer#restore} for a location, flags
 * or a state a factory doesn't make.
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

    private Location location = new Location.Missing();
    private Set<AddressFlag> flags = EnumSet.noneOf(AddressFlag.class);
    private Customer.State state = new Customer.State.Active();
    private Instant createdAt = CREATED;
    private boolean anonymized;

    private CustomerBuilder() {}

    static CustomerBuilder aCustomer() {
        return new CustomerBuilder();
    }

    CustomerBuilder createdAt(Instant at) {
        this.createdAt = at;
        return this;
    }

    CustomerBuilder geocodedAt(GeoPoint point, MatchQuality quality, AddressFlag... withFlags) {
        this.location = new Location.Geocoded(point, quality);
        this.flags = EnumSet.noneOf(AddressFlag.class);
        this.flags.addAll(Set.of(withFlags));
        return this;
    }

    CustomerBuilder geocodedAt(GeoPoint point, AddressFlag... withFlags) {
        return geocodedAt(point, MatchQuality.EXACT, withFlags);
    }

    CustomerBuilder flagged(AddressFlag flag) {
        this.flags = EnumSet.of(flag);
        return this;
    }

    CustomerBuilder placedByHandAt(GeoPoint point) {
        this.location = new Location.HandPlaced(point);
        this.flags = EnumSet.noneOf(AddressFlag.class);
        return this;
    }

    CustomerBuilder retiredAt(Instant at) {
        this.state = new Customer.State.Retired(at);
        return this;
    }

    CustomerBuilder anonymizedAt(Instant at) {
        this.state = new Customer.State.Anonymized(at);
        this.anonymized = true;
        this.location = new Location.Missing();
        this.flags = EnumSet.noneOf(AddressFlag.class);
        return this;
    }

    Customer registered() {
        return Customer.register(CUSTOMER, NAME, PHONE, ADDRESS, Optional.of(NOTES), DEFAULTS, createdAt);
    }

    CustomerSnapshot snapshot() {
        return new CustomerSnapshot(
                CUSTOMER,
                anonymized ? Optional.empty() : Optional.of(NAME),
                anonymized ? Optional.empty() : Optional.of(PHONE),
                anonymized ? Optional.empty() : Optional.of(ADDRESS),
                anonymized ? Optional.empty() : Optional.of(NOTES),
                location,
                flags,
                DEFAULTS,
                state,
                createdAt,
                Version.of(2));
    }

    Customer restored() {
        return Customer.restore(snapshot());
    }
}
