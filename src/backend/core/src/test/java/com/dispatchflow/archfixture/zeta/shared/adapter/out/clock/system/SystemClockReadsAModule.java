package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.system;

import com.dispatchflow.archfixture.zeta.upper.application.port.out.UpperRecords;

/** Rule 11, one level down: a class below a shared adapter uses a module's port. */
public record SystemClockReadsAModule(UpperRecords records) {}
