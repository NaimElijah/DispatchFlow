package com.dispatchflow.archfixture.zeta.report.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFacts;

/** Rule 8: a reader must not call a source module's api, though rules 1 and 2 allow it. */
public record ReportReadsTheApi(LowerFacts facts) {}
