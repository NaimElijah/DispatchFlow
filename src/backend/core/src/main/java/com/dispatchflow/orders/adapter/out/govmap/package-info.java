/**
 * Customers & Orders' GovMap adapter (ADR-085, ADR-130): implements {@code Geocoder}. Its cache of
 * geocoded addresses (FR-13) is a table, so it reaches the cache through a port that the module's
 * {@code persistence} adapter implements, never through a class of that package.
 */
@NullMarked
package com.dispatchflow.orders.adapter.out.govmap;

import org.jspecify.annotations.NullMarked;
