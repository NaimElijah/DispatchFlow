package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFacts;
import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;
import com.dispatchflow.archfixture.zeta.lower.events.LowerHappened;
import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.shared.application.ZetaClock;
import com.dispatchflow.archfixture.zeta.upper.application.port.out.UpperRecords;
import com.dispatchflow.archfixture.zeta.upper.domain.UpperValue;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rules 1 and 2: a use case that depends on everything rule 1 allows: the JDK, the kernel and the
 * shared application package, its own domain and ports, another module's api and events, and the
 * spi of a module whose interface it implements, and {@code @Transactional}; and, by rule 2, only
 * on {@code lower}, which the graph allows.
 */
public final class UpperUseCase {

    private final LowerFacts lowerFacts;
    private final LowerUsage lowerUsage;
    private final UpperRecords records;
    private final ZetaClock clock;

    UpperUseCase(LowerFacts lowerFacts, LowerUsage lowerUsage, UpperRecords records, ZetaClock clock) {
        this.lowerFacts = lowerFacts;
        this.lowerUsage = lowerUsage;
        this.records = records;
        this.clock = clock;
    }

    @Transactional
    void on(LowerHappened event) {
        if (lowerUsage.inUse(lowerFacts.fact(event.value()).value().amount())) {
            records.append(new UpperValue(clock.now().getNano()));
        }
    }

    ZetaKernelValue none() {
        return new ZetaKernelValue(0);
    }
}
