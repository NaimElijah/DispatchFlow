package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DeliveryDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The facts an import's rows are checked against, which the use case reads before it calls {@link
 * ImportBatch#preview} or {@link ImportBatch#confirm} (ADR-086): the active customers matching each
 * row's address, each date's uncancelled orders under the date's lock, and the company's defaults for
 * a customer the import creates (FR-125, FR-184, ADR-104). Confirmation reads them again, because
 * they may have changed since the preview.
 *
 * <p>It indexes the matches by address and the snapshots by date, so checking a file costs one lookup
 * per row, whatever its length. The indexes are only looked up, never iterated, so their order never
 * shows.
 */
public final class ImportContext {

    /**
     * The match for one normalized address.
     *
     * @param address the normalized address
     * @param match the active customers that have it
     */
    public record AddressMatch(NormalizedAddress address, CustomerMatch match) {

        /** Checks both parts are given. */
        public AddressMatch {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(match, "match");
        }
    }

    private final Map<NormalizedAddress, CustomerMatch> matches;
    private final Map<DeliveryDate, DateCapacity> capacities;
    private final OrderDefaults companyDefaults;

    private ImportContext(
            Map<NormalizedAddress, CustomerMatch> matches,
            Map<DeliveryDate, DateCapacity> capacities,
            OrderDefaults companyDefaults) {
        this.matches = matches;
        this.capacities = capacities;
        this.companyDefaults = companyDefaults;
    }

    /**
     * Builds the context.
     *
     * @param matches one match for each distinct normalized address in the file
     * @param capacities one snapshot for each distinct date in the file
     * @param companyDefaults the window and service time a new customer starts with
     * @return the context
     * @throws IllegalStateException from the JDK's collector when two matches share an address or two
     *     snapshots a date: a programming error in the use case
     */
    public static ImportContext of(
            List<AddressMatch> matches, List<DateCapacity> capacities, OrderDefaults companyDefaults) {
        return new ImportContext(
                matches.stream().collect(Collectors.toUnmodifiableMap(AddressMatch::address, AddressMatch::match)),
                capacities.stream().collect(Collectors.toUnmodifiableMap(DateCapacity::date, Function.identity())),
                Objects.requireNonNull(companyDefaults, "companyDefaults"));
    }

    /**
     * The window and service time a customer the import creates starts with.
     *
     * @return the company's defaults
     */
    public OrderDefaults companyDefaults() {
        return companyDefaults;
    }

    /**
     * The match for a row's address.
     *
     * @throws NoSuchElementException when the use case supplied none: a programming error, as with
     *     {@code FreshIds}, so no {@link OrdersError} describes it
     */
    CustomerMatch matchFor(NormalizedAddress address) {
        return Optional.ofNullable(matches.get(address))
                .orElseThrow(() -> new NoSuchElementException("the use case supplied no match for a row's address"));
    }

    /**
     * The snapshot for a row's date.
     *
     * @throws NoSuchElementException when the use case supplied none, a programming error
     */
    DateCapacity capacityFor(DeliveryDate date) {
        return Optional.ofNullable(capacities.get(date))
                .orElseThrow(() -> new NoSuchElementException("the use case supplied no capacity for a row's date"));
    }
}
