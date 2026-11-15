/**
 * The ports Customers &amp; Orders owns for other modules to implement (DL §2.1, §2.3 rules 2 and
 * 4): {@link com.dispatchflow.orders.application.spi.StopProgress}, which Live Operations
 * implements, and {@link com.dispatchflow.orders.application.spi.OrderPlacement} and {@link
 * com.dispatchflow.orders.application.spi.RecordUsage}, which Planning implements. So an order's
 * status and a customer's retirement read downstream facts without depending on downstream
 * modules.
 *
 * <p>Its types use only kernel types, JDK types and their own records, enums and sealed
 * interfaces (DL §2.3 rule 1).
 */
@NullMarked
package com.dispatchflow.orders.application.spi;

import org.jspecify.annotations.NullMarked;
