package com.dispatchflow.archfixture.shared;

import org.locationtech.jts.geom.Point;

/** Rule 1: the kernel may use libphonenumber, but JTS only a module's domain. */
public record KernelDependsOnJts(Point point) {}
