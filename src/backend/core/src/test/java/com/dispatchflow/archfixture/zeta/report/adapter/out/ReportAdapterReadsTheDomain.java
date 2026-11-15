package com.dispatchflow.archfixture.zeta.report.adapter.out;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;

/** Rule 8: it binds every class of the reader, its adapters as well as its application. */
public record ReportAdapterReadsTheDomain(LowerValue value) {}
