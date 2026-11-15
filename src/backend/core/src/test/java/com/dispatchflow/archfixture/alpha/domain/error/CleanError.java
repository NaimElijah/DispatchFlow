package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.StopId;

/** An error with only allowed parameters. */
public record CleanError(int count, DeliveryDate date, StopId stop) implements DomainError {}
