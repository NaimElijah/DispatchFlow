package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.upper.application.port.out.UpperRecords;

/**
 * Rule 13: {@code web} uses a module's outbound port, which is neither its {@code api} nor its
 * {@code events}.
 */
public record WebUsesAModulesPort(UpperRecords records) {}
