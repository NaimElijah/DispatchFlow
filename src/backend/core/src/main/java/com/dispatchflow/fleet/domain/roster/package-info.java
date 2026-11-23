/**
 * The roster aggregate (DL §4.2, §17): {@link com.dispatchflow.fleet.domain.roster.DayRoster}, one
 * delivery date's roster, with its entries and their stored form, shifts, availability, and the results of
 * filling it and of checking who can be assigned. The roster and its entries share this package
 * because the roster reaches its entries through package-private members (ADR-118).
 */
@NullMarked
package com.dispatchflow.fleet.domain.roster;

import org.jspecify.annotations.NullMarked;
