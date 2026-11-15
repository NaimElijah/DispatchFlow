package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.AccountId;

/**
 * Sends a visible push to a driver's phone through Expo's push service (ADR-026, TS §6.4). The
 * adapter finds the phone's push addresses through Access's {@code PushTargets} (placement P1). A
 * push carries no personal data (NFR-218).
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
}
