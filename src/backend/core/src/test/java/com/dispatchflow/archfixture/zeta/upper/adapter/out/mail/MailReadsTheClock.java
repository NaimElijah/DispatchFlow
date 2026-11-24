package com.dispatchflow.archfixture.zeta.upper.adapter.out.mail;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ZetaClock;

/** Rules 9 and 10 allow it: an adapter uses a shared adapter other than persistence, as the
 * optimizer adapters wrap the shared client (ADR-130 decision 5). */
public record MailReadsTheClock(ZetaClock clock) {}
