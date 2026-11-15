package com.dispatchflow.archfixture.zeta.upper.application.service;

import org.locationtech.jts.geom.Point;

/**
 * Rule 1: JTS and libphonenumber are the domain's libraries, so the application must not use them.
 */
public record DependsOnADomainLibrary(Point point) {}
