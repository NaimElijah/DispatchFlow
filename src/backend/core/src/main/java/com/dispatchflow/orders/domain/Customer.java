package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.CustomerAnonymized;
import com.dispatchflow.orders.domain.OrdersError.CustomerInUse;
import com.dispatchflow.orders.domain.OrdersError.CustomerRetired;
import com.dispatchflow.orders.domain.OrdersError.OutsideServiceArea;
import com.dispatchflow.orders.domain.OrdersError.RetentionNotOver;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A delivery address with a name, a contact phone and defaults (DL §4.3, §17). It owns the rules
 * about its location: a hand-placed location always wins over geocoding (BR-13, FR-215) and clears
 * every address flag (FR-217); a changed address clears it and is geocoded again (BR-104); a hand-
 * placed location lies in the service area (FR-320). It is retired, never deleted (BR-43), and
 * anonymized in place when its retention ends (BR-98, NFR-257, ADR-075).
 *
 * <p>Its default window and service time start as the company's, which Access's company profile
 * holds and the application layer passes in (FR-125, FR-184, ADR-104). The service time may be
 * absent; an order given none is then refused (BR-124).
 *
 * <p>Its name, phone, address, notes and location are personal data.
 */
public final class Customer extends AggregateRoot implements PersonalData {

    /** A geocoded point farther than this from the nearest road is flagged (FR-63); a starting value. */
    public static final double FAR_FROM_ROAD_METRES = 300;

    /** How long after retirement, or after the last order, a customer's personal data is kept (NFR-257, ADR-075). */
    public static final Period RETENTION = Period.ofYears(1);

    /** Whether the customer is active, retired or anonymized (DL §4.3). */
    public sealed interface State {

        /** The customer can be matched, changed and given orders. */
        record Active() implements State {}

        /**
         * The customer was retired (BR-43, FR-56).
         *
         * @param at when it was retired
         */
        record Retired(Instant at) implements State {

            /** Checks the time is given. */
            public Retired {
                Objects.requireNonNull(at, "at");
            }
        }

        /**
         * The customer's personal data was removed; the record stays, because orders and plans refer
         * to it (BR-98, ADR-075).
         *
         * @param at when it was anonymized
         */
        record Anonymized(Instant at) implements State {

            /** Checks the time is given. */
            public Anonymized {
                Objects.requireNonNull(at, "at");
            }
        }
    }

    private final CustomerId id;
    private @Nullable ContactName name;
    private @Nullable PhoneNumber phone;
    private @Nullable Address address;
    private @Nullable DriverNote note;
    private Location location;
    private final EnumSet<AddressFlag> flags;
    private final OrderDefaults defaults;
    private State state;
    private final Instant createdAt;

    private Customer(CustomerSnapshot snapshot) {
        super(snapshot.version());
        this.id = snapshot.id();
        this.name = snapshot.name().orElse(null);
        this.phone = snapshot.phone().orElse(null);
        this.address = snapshot.address().orElse(null);
        this.note = snapshot.note().orElse(null);
        this.location = snapshot.location();
        this.flags = EnumSet.noneOf(AddressFlag.class);
        this.flags.addAll(snapshot.flags());
        this.defaults = snapshot.defaults();
        this.state = snapshot.state();
        this.createdAt = snapshot.createdAt();
    }

    /**
     * Registers a customer a dispatcher created (FR-08, FR-59). It has no location until geocoding
     * answers or a dispatcher places one.
     *
     * @param id the new customer's ID
     * @param name its name
     * @param phone its contact phone
     * @param address its address as typed
     * @param note its standing notes for drivers, if any (FR-08)
     * @param defaults its default window and service time, from the form or the company's (FR-125)
     * @param now the time of creation, from which a customer that never gets an order counts its
     *     retention (NFR-257, ADR-102)
     * @return the customer, never saved
     */
    public static Customer register(
            CustomerId id,
            ContactName name,
            PhoneNumber phone,
            Address address,
            Optional<DriverNote> note,
            OrderDefaults defaults,
            Instant now) {
        return newCustomer(id, name, phone, address, note, defaults, Set.of(), now);
    }

    /** Registers a customer an import row created, flagged for a location check (FR-311). */
    static Customer registerFromImport(
            CustomerId id, ContactName name, PhoneNumber phone, Address address, OrderDefaults defaults, Instant now) {
        return newCustomer(
                id, name, phone, address, Optional.empty(), defaults, Set.of(AddressFlag.CREATED_BY_IMPORT), now);
    }

    private static Customer newCustomer(
            CustomerId id,
            ContactName name,
            PhoneNumber phone,
            Address address,
            Optional<DriverNote> note,
            OrderDefaults defaults,
            Set<AddressFlag> flags,
            Instant now) {
        return new Customer(new CustomerSnapshot(
                id,
                Optional.of(name),
                Optional.of(phone),
                Optional.of(address),
                note,
                new Location.Missing(),
                flags,
                defaults,
                new State.Active(),
                now,
                null));
    }

    /**
     * Rebuilds a stored customer, checking the rules that hold at rest. Only the persistence adapter
     * calls it (DL §3.5).
     *
     * @param snapshot the stored customer
     * @return the customer, at the stored version
     * @throws StoredStateInvalid when an anonymized customer still holds personal data, a customer
     *     not anonymized lacks its name, phone or address, a hand-placed location carries a flag, or
     *     its retirement or anonymization is older than its creation
     */
    public static Customer restore(CustomerSnapshot snapshot) {
        boolean holdsPersonalData = snapshot.name().isPresent()
                || snapshot.phone().isPresent()
                || snapshot.address().isPresent()
                || snapshot.note().isPresent()
                || !(snapshot.location() instanceof Location.Missing)
                || !snapshot.flags().isEmpty();
        boolean anonymized = snapshot.state() instanceof State.Anonymized;
        if (anonymized && holdsPersonalData) {
            throw new StoredStateInvalid(Customer.class, "an anonymized customer holds personal data");
        }
        boolean complete = snapshot.name().isPresent()
                && snapshot.phone().isPresent()
                && snapshot.address().isPresent();
        if (!anonymized && !complete) {
            throw new StoredStateInvalid(Customer.class, "a customer lacks its name, phone or address");
        }
        if (snapshot.location() instanceof Location.HandPlaced
                && !snapshot.flags().isEmpty()) {
            throw new StoredStateInvalid(Customer.class, "a hand-placed location carries an address flag");
        }
        Optional<Instant> endedAt = switch (snapshot.state()) {
            case State.Active active -> Optional.empty();
            case State.Retired retired -> Optional.of(retired.at());
            case State.Anonymized anonymizedAt -> Optional.of(anonymizedAt.at());
        };
        if (endedAt.filter(at -> at.isBefore(snapshot.createdAt())).isPresent()) {
            throw new StoredStateInvalid(Customer.class, "the customer's state is older than its creation");
        }
        return new Customer(snapshot);
    }

    /**
     * Changes the address (BR-104). A different address clears the location, hand-placed or not,
     * and its flags, so the customer is geocoded again; the same text changes nothing. The dispatcher
     * is warned before, by the screen.
     *
     * @param newAddress the address as typed
     * @return {@code true} when the address changed, so the use case requests geocoding
     * @throws RuleViolation with {@link CustomerAnonymized} or {@link CustomerRetired}
     */
    public boolean changeAddress(Address newAddress) {
        refuseUnlessActive();
        if (newAddress.equals(address)) {
            return false;
        }
        address = newAddress;
        location = new Location.Missing();
        flags.clear();
        return true;
    }

    /**
     * Records the geocoder's answer (FR-12, FR-63). It is ignored when a location was placed by hand
     * (BR-13) or when the answer is for an address the customer no longer has (BR-104). A found point
     * becomes the location, flagged when the match is partial or ambiguous, outside the service area,
     * or more than {@value #FAR_FROM_ROAD_METRES} m from a road. No match, or a lookup the outbox gave
     * up on after 24 hours (FR-64, DL §12), flags the missing match and keeps no location.
     *
     * @param result the geocoder's answer
     * @param area the company's service area
     * @return {@code true} when the answer was recorded
     */
    public boolean recordGeocode(GeocodeResult result, ServiceArea area) {
        boolean current = normalizedAddress().filter(result.lookedUp()::equals).isPresent();
        if (!current || location instanceof Location.HandPlaced) {
            return false;
        }
        EnumSet<AddressFlag> found = EnumSet.noneOf(AddressFlag.class);
        switch (result) {
            case GeocodeResult.Found hit -> {
                location = new Location.Geocoded(hit.at(), hit.quality());
                switch (hit.quality()) {
                    case EXACT -> {}
                    case PARTIAL -> found.add(AddressFlag.PARTIAL_MATCH);
                    case AMBIGUOUS -> found.add(AddressFlag.AMBIGUOUS_MATCH);
                }
                if (!area.contains(hit.at())) {
                    found.add(AddressFlag.OUTSIDE_SERVICE_AREA);
                }
                if (hit.metresToRoad() > FAR_FROM_ROAD_METRES) {
                    found.add(AddressFlag.FAR_FROM_ROAD);
                }
            }
            case GeocodeResult.NotFound notFound -> {
                location = new Location.Missing();
                found.add(AddressFlag.NO_MATCH);
            }
            case GeocodeResult.GaveUp gaveUp -> {
                location = new Location.Missing();
                found.add(AddressFlag.NO_MATCH);
            }
        }
        // An import's location-check flag stays until a dispatcher places the location (FR-311).
        if (flags.contains(AddressFlag.CREATED_BY_IMPORT)) {
            found.add(AddressFlag.CREATED_BY_IMPORT);
        }
        flags.clear();
        flags.addAll(found);
        return true;
    }

    /**
     * Places the location by hand (FR-14, FR-218). It wins over every later geocoding result
     * (BR-13), is used for every future order (FR-215), and clears every address flag (FR-217).
     *
     * @param point the point placed on the map
     * @param area the company's service area
     * @throws RuleViolation with {@link CustomerAnonymized}, {@link CustomerRetired} or {@link
     *     OutsideServiceArea} (FR-320), in that order
     */
    public void placeLocation(GeoPoint point, ServiceArea area) {
        refuseUnlessActive();
        if (!area.contains(point)) {
            throw new RuleViolation(new OutsideServiceArea());
        }
        location = new Location.HandPlaced(point);
        flags.clear();
    }

    /**
     * Retires the customer (BR-43, FR-56). It is no longer matched by imports, and its record stays.
     *
     * @param usage the plans not yet completed that use it, from Planning
     * @param now the time of retiring, from which its retention counts; a time before its creation,
     *     from a clock that stepped back, is recorded as its creation's, so the customer still
     *     restores (ADR-115)
     * @throws RuleViolation with {@link CustomerAnonymized}, {@link CustomerRetired} or {@link
     *     CustomerInUse}, in that order
     */
    public void retire(InUse usage, Instant now) {
        refuseUnlessActive();
        if (usage.isInUse()) {
            throw new RuleViolation(new CustomerInUse(id, usage.plans()));
        }
        state = new State.Retired(now.isBefore(createdAt) ? createdAt : now);
    }

    /**
     * Removes the customer's personal data in place once its retention is over (BR-98, NFR-257,
     * ADR-075): a year after it was retired, or, for a customer never retired, a year after the later
     * of its creation and its last order, counted in Israel's calendar (ADR-102). The defaults stay,
     * because they are not personal.
     *
     * @param lastDeliveryDate the delivery date of its last order, if it has any
     * @param now the time of the retention job
     * @throws RuleViolation with {@link CustomerAnonymized} when already done, or with {@link
     *     RetentionNotOver}
     */
    public void anonymize(Optional<DeliveryDate> lastDeliveryDate, Instant now) {
        boolean over = switch (state) {
            case State.Anonymized anonymized -> throw new RuleViolation(new CustomerAnonymized(id));
            case State.Retired retired ->
                !now.isBefore(
                        retired.at().atZone(IsraelDateTime.ZONE).plus(RETENTION).toInstant());
            case State.Active active ->
                !today(now).isBefore(lastActivity(lastDeliveryDate).plus(RETENTION));
        };
        if (!over) {
            throw new RuleViolation(new RetentionNotOver());
        }
        name = null;
        phone = null;
        address = null;
        note = null;
        location = new Location.Missing();
        flags.clear();
        state = new State.Anonymized(now);
    }

    private static LocalDate today(Instant now) {
        return IsraelDateTime.at(now).date().value();
    }

    /** The later of the day it was created and its last order's delivery date, in Israel. */
    private LocalDate lastActivity(Optional<DeliveryDate> lastDeliveryDate) {
        LocalDate created = today(createdAt);
        return lastDeliveryDate
                .map(DeliveryDate::value)
                .filter(last -> last.isAfter(created))
                .orElse(created);
    }

    private void refuseUnlessActive() {
        switch (state) {
            case State.Anonymized anonymized -> throw new RuleViolation(new CustomerAnonymized(id));
            case State.Retired retired -> throw new RuleViolation(new CustomerRetired(id));
            case State.Active active -> {}
        }
    }

    /**
     * The customer's ID.
     *
     * @return the ID
     */
    public CustomerId id() {
        return id;
    }

    /**
     * The form its address is matched by (FR-310).
     *
     * @return the normalized address, or empty once anonymized
     */
    public Optional<NormalizedAddress> normalizedAddress() {
        return Optional.ofNullable(address).map(NormalizedAddress::of);
    }

    /**
     * What its new orders take when given no window or service time (BR-49, BR-124).
     *
     * @return its defaults
     */
    public OrderDefaults orderDefaults() {
        return defaults;
    }

    /**
     * Where the customer is.
     *
     * @return its location
     */
    public Location location() {
        return location;
    }

    /**
     * Its doubts about the location (FR-63).
     *
     * @return the flags, in their declared order, unmodifiable
     */
    public Set<AddressFlag> flags() {
        return snapshot().flags();
    }

    /**
     * Whether it is active, retired or anonymized.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Everything the customer holds, for the persistence adapter and the read side.
     *
     * @return the snapshot, at the loaded version
     */
    public CustomerSnapshot snapshot() {
        return new CustomerSnapshot(
                id,
                Optional.ofNullable(name),
                Optional.ofNullable(phone),
                Optional.ofNullable(address),
                Optional.ofNullable(note),
                location,
                flags,
                defaults,
                state,
                createdAt,
                savedVersion());
    }

    /**
     * A form that hides the customer, because it holds personal data.
     *
     * @return {@code Customer[redacted]}
     */
    @Override
    public String toString() {
        return "Customer[redacted]";
    }
}
