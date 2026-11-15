package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;

/**
 * Sends a visible push to a driver's or the staff's phones through Expo's push service (ADR-026,
 * ADR-083, TS §6.4). The adapter finds the addresses through Access's {@code PushTargets}
 * (placement P1): {@code of} for a driver, {@code ofStaff} for an alert. When Expo reports a device
 * gone, the adapter calls {@code PushTargets.forget} with that address and its own server step, so
 * Access removes it (NFR-263). A push carries no personal data (NFR-218).
 *
 * <p>Built in M10.
 */
public interface PushNotifier {

    /**
     * Tells a driver that something changed. A driver with no registered phone gets nothing, which
     * is not an error: the web build reads its route every 30 seconds (FR-347).
     *
     * <p>Built in M10.
     *
     * @param driver the driver's account
     * @param notice what changed
     */
    void tellDriver(AccountId driver, DriverNotice notice);

    /**
     * Tells every Admin and Dispatcher with a registered phone that an alert was raised, so a
     * phone app in the background learns of it; the app fetches the alert itself (FR-405, ADR-083).
     * The push names nothing about the alert (NFR-218). The adapter sends it to {@code
     * PushTargets.ofStaff}, the live sessions of the active Admins and Dispatchers.
     *
     * <p>Built in M10.
     *
     * @param alert the alert raised
     */
    void alertStaff(AlertId alert);
}
