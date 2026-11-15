package com.dispatchflow.archfixture.zeta.report.application.service;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateAccount;

/**
 * Rule 8: a reader must not use the actor's module's other api types, which rules 1 and 2 allow.
 */
public record ReportReadsAnAccount(GateAccount account) {}
