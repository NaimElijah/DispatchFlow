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
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 *
 * <p>It is mapped for JPA (ADR-126): its key is its ID's UUID, its {@link Location} and its {@link
 * State} each a kind and nullable columns, its flags a set of names, and its defaults, which never
 * change after registration, columns of its own. Its normalized address is stored beside its
 * address, so {@code CustomerRepository.candidatesAt} finds a match with an index. Every method
 * checks all its rules before it changes anything, and every change ends by checking the
 * invariants at rest.
 */
@Entity
@Table(schema = "orders", name = "customer")
public class Customer extends AggregateRoot implements PersonalData {

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

    /** Which {@link Location} a customer has. */
    private enum LocationKind {
        MISSING,
        GEOCODED,
        HAND_PLACED
    }

    /** Which {@link State} a customer is in. */
    private enum StateKind {
        ACTIVE,
        RETIRED,
        ANONYMIZED
    }

    @Id
    private UUID id;

    private @Nullable ContactName name;

    private @Nullable PhoneNumber phone;

    private @Nullable Address address;

    /** The address's matching form (FR-310), kept beside it for the lookup; null exactly when it is. */
    private @Nullable String normalizedAddress;

    private @Nullable DriverNote note;

    @Enumerated(EnumType.STRING)
    private LocationKind locationKind;

    @Embedded
    @AttributeOverride(name = "latitude", column = @Column(name = "location_latitude"))
    @AttributeOverride(name = "longitude", column = @Column(name = "location_longitude"))
    private @Nullable GeoPoint locationPoint;

    @Enumerated(EnumType.STRING)
    private @Nullable MatchQuality matchQuality;

    @ElementCollection
    @CollectionTable(schema = "orders", name = "customer_address_flag", joinColumns = @JoinColumn(name = "customer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "flag")
    private Set<AddressFlag> flags = EnumSet.noneOf(AddressFlag.class);

    @Embedded
    @AttributeOverride(name = "start", column = @Column(name = "default_window_starts_at"))
    @AttributeOverride(name = "end", column = @Column(name = "default_window_ends_at"))
    private DailyWindow defaultWindow;

    @Column(name = "default_service_time_seconds")
    private @Nullable ServiceTime defaultServiceTime;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    /** When a retired or anonymized customer reached its state; null while active. */
    private @Nullable Instant stateSince;

    private Instant createdAt;

    /** For Hibernate, which sets the fields from the stored rows and then runs {@link #checkStored}. */
    protected Customer() {}

    private Customer(
            CustomerId id,
            ContactName name,
            PhoneNumber phone,
            Address address,
            Optional<DriverNote> note,
            OrderDefaults defaults,
            Set<AddressFlag> flags,
            Instant now) {
        this.id = id.value();
        this.name = name;
        this.phone = phone;
        takeAddress(Optional.of(address));
        this.note = note.orElse(null);
        locateAt(new Location.Missing());
        this.flags.addAll(flags);
        this.defaultWindow = defaults.window();
        this.defaultServiceTime = defaults.serviceTime().orElse(null);
        this.state = StateKind.ACTIVE;
        this.createdAt = now;
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
        Customer customer = new Customer(id, name, phone, address, note, defaults, flags, now);
        customer.requireInvariants();
        return customer;
    }

    /**
     * Checks the customer as Hibernate loaded it against the invariants at rest (ADR-126 decision 8):
     * an anonymized customer holds no personal data, a customer not anonymized has its name, phone
     * and address, a hand-placed location carries no flag, and its retirement or anonymization is not
     * older than its creation.
     *
     * @throws StoredStateInvalid when the stored state breaks one of them
     */
    @PostLoad
    void checkStored() {
        brokenRule().ifPresent(broken -> {
            throw new StoredStateInvalid(Customer.class, broken);
        });
    }

    /** The first invariant at rest the customer breaks, if any: those {@link #checkStored} names. */
    private Optional<String> brokenRule() {
        boolean holdsPersonalData = name != null
                || phone != null
                || address != null
                || note != null
                || locationKind != LocationKind.MISSING
                || !flags.isEmpty();
        boolean anonymized = state == StateKind.ANONYMIZED;
        if (anonymized && holdsPersonalData) {
            return Optional.of("an anonymized customer holds personal data");
        }
        boolean complete = name != null && phone != null && address != null;
        if (!anonymized && !complete) {
            return Optional.of("a customer lacks its name, phone or address");
        }
        if (locationKind == LocationKind.HAND_PLACED && !flags.isEmpty()) {
            return Optional.of("a hand-placed location carries an address flag");
        }
        if (stateSince != null && stateSince.isBefore(createdAt)) {
            return Optional.of("the customer's state is older than its creation");
        }
        return Optional.empty();
    }

    /**
     * Checks the state a command left against every invariant at rest (ADR-126 decision 11). Every
     * rule a caller can break was checked before the command changed anything, so a broken
     * invariant here is a bug in the command, not a refusal, and the error rolls its transaction
     * back.
     */
    private void requireInvariants() {
        brokenRule().ifPresent(broken -> {
            throw new AssertionError("a customer command left a state that breaks: " + broken);
        });
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
        takeAddress(Optional.of(newAddress));
        locateAt(new Location.Missing());
        flags.clear();
        requireInvariants();
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
        if (!current || locationKind == LocationKind.HAND_PLACED) {
            return false;
        }
        EnumSet<AddressFlag> found = EnumSet.noneOf(AddressFlag.class);
        switch (result) {
            case GeocodeResult.Found hit -> {
                locateAt(new Location.Geocoded(hit.at(), hit.quality()));
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
                locateAt(new Location.Missing());
                found.add(AddressFlag.NO_MATCH);
            }
            case GeocodeResult.GaveUp gaveUp -> {
                locateAt(new Location.Missing());
                found.add(AddressFlag.NO_MATCH);
            }
        }
        // An import's location-check flag stays until a dispatcher places the location (FR-311).
        if (flags.contains(AddressFlag.CREATED_BY_IMPORT)) {
            found.add(AddressFlag.CREATED_BY_IMPORT);
        }
        flags.clear();
        flags.addAll(found);
        requireInvariants();
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
        locateAt(new Location.HandPlaced(point));
        flags.clear();
        requireInvariants();
    }

    /**
     * Retires the customer (BR-43, FR-56). It is no longer matched by imports, and its record stays.
     *
     * @param usage the plans not yet completed that use it, from Planning
     * @param now the time of retiring, from which its retention counts; a time before its creation,
     *     from a clock that stepped back, is recorded as its creation's, so its state is never older
     *     than its creation (ADR-115)
     * @throws RuleViolation with {@link CustomerAnonymized}, {@link CustomerRetired} or {@link
     *     CustomerInUse}, in that order
     */
    public void retire(InUse usage, Instant now) {
        refuseUnlessActive();
        if (usage.isInUse()) {
            throw new RuleViolation(new CustomerInUse(id(), usage.plans()));
        }
        endIn(StateKind.RETIRED, now.isBefore(createdAt) ? createdAt : now);
        requireInvariants();
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
        boolean over = switch (state()) {
            case State.Anonymized anonymized -> throw new RuleViolation(new CustomerAnonymized(id()));
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
        takeAddress(Optional.empty());
        note = null;
        locateAt(new Location.Missing());
        flags.clear();
        endIn(StateKind.ANONYMIZED, now);
        requireInvariants();
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
        switch (state()) {
            case State.Anonymized anonymized -> throw new RuleViolation(new CustomerAnonymized(id()));
            case State.Retired retired -> throw new RuleViolation(new CustomerRetired(id()));
            case State.Active active -> {}
        }
    }

    /**
     * The customer's ID.
     *
     * @return the ID
     */
    public CustomerId id() {
        return CustomerId.of(id);
    }

    /**
     * Its name (FR-59).
     *
     * @return the name, or empty once anonymized
     */
    public Optional<ContactName> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Its contact phone (FR-59).
     *
     * @return the phone, or empty once anonymized
     */
    public Optional<PhoneNumber> phone() {
        return Optional.ofNullable(phone);
    }

    /**
     * Its address as typed (FR-08).
     *
     * @return the address, or empty once anonymized
     */
    public Optional<Address> address() {
        return Optional.ofNullable(address);
    }

    /**
     * Its standing notes for drivers (FR-08).
     *
     * @return the note, or empty when it has none or once anonymized
     */
    public Optional<DriverNote> note() {
        return Optional.ofNullable(note);
    }

    /**
     * When it was registered, from which a customer with no order counts its retention (NFR-257,
     * ADR-102).
     *
     * @return the time of registration
     */
    public Instant createdAt() {
        return createdAt;
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
        return new OrderDefaults(defaultWindow, Optional.ofNullable(defaultServiceTime));
    }

    /**
     * Where the customer is.
     *
     * @return its location
     */
    public Location location() {
        return switch (locationKind) {
            case MISSING -> new Location.Missing();
            case GEOCODED ->
                new Location.Geocoded(
                        Objects.requireNonNull(locationPoint, "a geocoded location has a point"),
                        Objects.requireNonNull(matchQuality, "a geocoded location has a match quality"));
            case HAND_PLACED ->
                new Location.HandPlaced(Objects.requireNonNull(locationPoint, "a hand-placed location has a point"));
        };
    }

    /**
     * Its doubts about the location (FR-63).
     *
     * @return the flags, in their declared order, unmodifiable
     */
    public Set<AddressFlag> flags() {
        EnumSet<AddressFlag> declaredOrder = EnumSet.noneOf(AddressFlag.class);
        declaredOrder.addAll(flags);
        return Collections.unmodifiableSet(declaredOrder);
    }

    /**
     * Whether it is active, retired or anonymized.
     *
     * @return the state
     */
    public State state() {
        return switch (state) {
            case ACTIVE -> new State.Active();
            case RETIRED ->
                new State.Retired(Objects.requireNonNull(stateSince, "a retired customer has its retirement time"));
            case ANONYMIZED ->
                new State.Anonymized(
                        Objects.requireNonNull(stateSince, "an anonymized customer has its anonymization time"));
        };
    }

    /**
     * The customer as an import row's candidate (FR-60, FR-310): its contact and its defaults.
     *
     * @return the candidate, or empty unless the customer is active, because a retired or
     *     anonymized customer is never matched
     */
    public Optional<CustomerMatch.Candidate> asCandidate() {
        if (state != StateKind.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(new CustomerMatch.Candidate(
                id(),
                Objects.requireNonNull(name, "an active customer has a name"),
                Objects.requireNonNull(phone, "an active customer has a phone"),
                orderDefaults()));
    }

    /** Sets the address and its matching form together, in place. */
    private void takeAddress(Optional<Address> newAddress) {
        address = newAddress.orElse(null);
        normalizedAddress = newAddress
                .map(NormalizedAddress::of)
                .map(NormalizedAddress::text)
                .orElse(null);
    }

    /** Sets the location's kind, point and match quality, in place. */
    private void locateAt(Location next) {
        locationPoint = next.point().orElse(null);
        matchQuality = null;
        locationKind = switch (next) {
            case Location.Missing missing -> LocationKind.MISSING;
            case Location.Geocoded geocoded -> {
                matchQuality = geocoded.quality();
                yield LocationKind.GEOCODED;
            }
            case Location.HandPlaced handPlaced -> LocationKind.HAND_PLACED;
        };
    }

    /** Moves the customer to the retired or anonymized state, reached at a time, in place. */
    private void endIn(StateKind ended, Instant at) {
        state = ended;
        stateSince = at;
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
