/**
 * Fleet's GovMap adapter (ADR-120 P4, ADR-131): implements the port of Fleet's own that geocodes
 * a depot's address; that port is not declared yet. Fleet and Customers & Orders have no
 * relationship, and no adapter uses another module's (ADR-130 decision 7), so it doesn't share
 * Customers & Orders'.
 */
@NullMarked
package com.dispatchflow.fleet.adapter.out.govmap;

import org.jspecify.annotations.NullMarked;
