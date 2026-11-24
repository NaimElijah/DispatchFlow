package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.upper.application.port.out.UpperRecords;

/** Rule 11: a shared adapter uses a module's port, which rules 9 and 10 can't see. */
public record ClockReadsAModulesPort(UpperRecords records) {}
