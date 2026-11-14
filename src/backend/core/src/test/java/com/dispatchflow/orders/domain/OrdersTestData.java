package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Fixed values and the date snapshots the Orders tests share. Nothing here reads the clock. */
final class OrdersTestData {

    /** Tuesday 15 June 2027 in Israel, a 24-hour day. */
    static final LocalDate TODAY = LocalDate.of(2027, 6, 15);

    static final DeliveryDate TODAY_DATE = DeliveryDate.of(TODAY);
    static final DeliveryDate TOMORROW = DeliveryDate.of(TODAY.plusDays(1));
    static final DeliveryDate NEXT_WEEK = DeliveryDate.of(TODAY.plusDays(7));
    static final DeliveryDate YESTERDAY = DeliveryDate.of(TODAY.minusDays(1));

    /** 09:00 in Israel on {@link #TODAY}. */
    static final Instant NOW = Instant.parse("2027-06-15T06:00:00Z");

    static final Instant LATER = NOW.plusSeconds(600);

    static final OrderId ORDER = OrderId.of(UUID.fromString("0190a5e2-0000-7000-8000-00000000a001"));
    static final CustomerId CUSTOMER = CustomerId.of(UUID.fromString("0190a5e2-0000-7000-8000-00000000c001"));

    static final DailyWindow NINE_TO_FIVE = DailyWindow.of(LocalTime.of(9, 0), LocalTime.of(17, 0));
    static final ServiceTime FIVE_MINUTES = ServiceTime.ofSeconds(300);
    static final OrderDefaults DEFAULTS = new OrderDefaults(NINE_TO_FIVE, Optional.of(FIVE_MINUTES));
    static final OrderDefaults NO_DEFAULT_SERVICE_TIME = new OrderDefaults(NINE_TO_FIVE, Optional.empty());

    static final ExternalReference REFERENCE = ExternalReference.of("SHOP-1001").orElseThrow();
    static final Recipient RECIPIENT =
            new Recipient(Optional.of(ContactName.of("דנה לוי")), Optional.of(PhoneNumber.parse("052-345-6789")));
    static final AccessDetails ACCESS = AccessDetails.of("3", "12", "1234#");
    static final DriverNote NOTE = DriverNote.of("לא לצלצל, תינוק ישן").orElseThrow();

    private OrdersTestData() {}

    static TimeWindow tenToNoon(DeliveryDate date) {
        return TimeWindow.on(date, LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    static Weight kilograms(String value) {
        return Weight.kilograms(new BigDecimal(value));
    }

    static Volume litres(String value) {
        return Volume.litres(new BigDecimal(value));
    }

    static OrderId orderId(int n) {
        return OrderId.of(new UUID(0x0190a5e200007000L, 0x8000000000000000L | n));
    }

    static CustomerId customerId(int n) {
        return CustomerId.of(new UUID(0x0190a5e200007000L, 0x800000000000c000L | n));
    }

    /** Details with their own window on the date, their own service time and every optional part. */
    static OrderDetails details(DeliveryDate date) {
        return new OrderDetails(
                Optional.of(tenToNoon(date)),
                kilograms("12.5"),
                litres("40"),
                Optional.of(ServiceTime.ofSeconds(180)),
                Priority.of(2),
                true,
                Optional.of(REFERENCE),
                RECIPIENT,
                Optional.of(NOTE),
                ACCESS);
    }

    /** Details with no window, no service time and no reference, so the defaults decide. */
    static OrderDetails bareDetails() {
        return new OrderDetails(
                Optional.empty(),
                kilograms("1"),
                litres("1"),
                Optional.empty(),
                Priority.byDefault(),
                false,
                Optional.empty(),
                Recipient.NONE,
                Optional.empty(),
                AccessDetails.NONE);
    }

    static DateCapacity emptyDate(DeliveryDate date) {
        return DateCapacity.readUnderLock(date, List.of());
    }

    /** A date holding {@code count} other orders with no reference. */
    static DateCapacity dateWith(DeliveryDate date, int count) {
        return DateCapacity.readUnderLock(
                date,
                IntStream.rangeClosed(1, count)
                        .mapToObj(n -> new DateCapacity.DatedOrder(orderId(n), Optional.empty()))
                        .toList());
    }

    /** A date holding {@code count} other orders and the given one. */
    static DateCapacity dateWithAnd(DeliveryDate date, int count, OrderId order) {
        return DateCapacity.readUnderLock(
                date,
                Stream.concat(
                                IntStream.rangeClosed(1, count)
                                        .mapToObj(n -> new DateCapacity.DatedOrder(orderId(n), Optional.empty())),
                                Stream.of(new DateCapacity.DatedOrder(order, Optional.empty())))
                        .toList());
    }

    /** A date where another order already carries the reference. */
    static DateCapacity dateWithReference(DeliveryDate date, ExternalReference reference) {
        return DateCapacity.readUnderLock(
                date, List.of(new DateCapacity.DatedOrder(orderId(900), Optional.of(reference))));
    }
}
