/**
 * A dispatcher's use cases on a stop's {@code Delivery} (DL §4.5, §8): confirming or refusing a held
 * report (BR-132, BR-135), recording an outcome on a driver's behalf (FR-73), and correcting an
 * outcome (FR-333). Each is a use case and the {@code Change} that holds its transaction (ADR-129).
 */
@NullMarked
package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import org.jspecify.annotations.NullMarked;
