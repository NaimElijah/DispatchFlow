/**
 * The ports Fleet owns for other modules to implement (DL §2.1, §2.3 rules 2 and 4): {@link
 * com.dispatchflow.fleet.application.spi.RecordUsage}, which Planning implements, so Fleet can ask
 * which plans use a depot, a vehicle or a driver without depending on Planning.
 *
 * <p>Its types use only kernel types, JDK types and their own records (DL §2.3 rule 1).
 */
@NullMarked
package com.dispatchflow.fleet.application.spi;

import org.jspecify.annotations.NullMarked;
