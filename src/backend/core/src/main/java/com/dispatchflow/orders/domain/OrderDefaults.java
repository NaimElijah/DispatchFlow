package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What an order takes when it is given no window or no service time (BR-49, BR-124): a customer's
 * defaults, or, for a customer an import creates, the company's (FR-125, FR-184, ADR-104).
 *
 * @param window the default window
 * @param serviceTime the default service time; empty where none is set, and then an order given
 *     none is refused (BR-124)
 */
public record OrderDefaults(DailyWindow window, Optional<ServiceTime> serviceTime) {

    /** Checks both parts are given. */
    public OrderDefaults {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(serviceTime, "serviceTime");
    }
}
