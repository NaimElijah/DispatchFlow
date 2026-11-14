package com.dispatchflow.reporting.domain;

import com.dispatchflow.reporting.domain.ReportingError.InvalidOnTimeCounts;
import com.dispatchflow.shared.RuleViolation;

/** The one check both measured rates make, so a share can't be built from counts that are not one. */
final class OnTimeCounts {

    private static final int FIRST_COUNTED = 1;

    private OnTimeCounts() {}

    /**
     * Checks that the counts are a share: at least one counted, and the on-time count from 0 to the
     * total.
     *
     * @throws RuleViolation with {@link InvalidOnTimeCounts} when they are not
     */
    static void requireShare(int onTime, int total) {
        if (total < FIRST_COUNTED || onTime < 0 || onTime > total) {
            throw new RuleViolation(new InvalidOnTimeCounts(onTime, total));
        }
    }
}
