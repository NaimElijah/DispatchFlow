package com.dispatchflow.archfixture.zeta.upper;

import com.dispatchflow.archfixture.zeta.upper.application.service.used.UsedByTheServiceRoot;
import org.springframework.context.annotation.Import;

/**
 * Rule 15: a configuration that is public. Its one import, a use case in a subpackage of its own
 * module's {@code application.service}, which ADR-125 lets a service package split into, is
 * allowed, so being public is its only violation.
 */
@Import(UsedByTheServiceRoot.class)
public class UpperConfiguration {}
