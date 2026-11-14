package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.OrderId;

/** An order and attempt, which a plan serves or drops at most once (BR-07). */
record StopKey(OrderId order, Attempt attempt) {}
