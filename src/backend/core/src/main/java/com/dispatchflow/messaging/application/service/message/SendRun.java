package com.dispatchflow.messaging.application.service.message;

/**
 * What one run of the sender did: how many messages the provider accepted, and how many it dropped
 * as stale, 15 minutes after their trigger with no acceptance (NFR-190). The job adapter records the
 * dropped count as a metric, as NFR-161 measures the others.
 *
 * <p>Built in M12.
 *
 * @param accepted how many messages the provider accepted in the run, 0 or more
 * @param dropped how many messages the run dropped as stale, 0 or more
 */
public record SendRun(int accepted, int dropped) {}
