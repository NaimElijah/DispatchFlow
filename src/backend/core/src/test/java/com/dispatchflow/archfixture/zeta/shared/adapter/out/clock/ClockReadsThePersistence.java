package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.persistence.ZetaKernelAdapter;

/** Rule 9: the adapters beside the kernel stay apart from each other too. */
public record ClockReadsThePersistence(ZetaKernelAdapter persistence) {}
