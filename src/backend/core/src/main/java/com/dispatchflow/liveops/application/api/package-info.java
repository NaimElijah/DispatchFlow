/**
 * Live Operations' published API (DL §2.2, §2.3 rule 4): {@link
 * com.dispatchflow.liveops.application.api.RaiseAlert}, which Messaging calls on a low rating;
 * {@link com.dispatchflow.liveops.application.api.ArrivalProjections}, which Messaging's minute job
 * asks; and {@link com.dispatchflow.liveops.application.api.StopResults}, which Reporting reads.
 *
 * <p>Its types use only kernel types, JDK types, their own records, enums and sealed interfaces,
 * and Audit's {@code CallContext}, never a domain type (DL §2.3 rule 1). The caller translates each
 * answer into its own values.
 */
@NullMarked
package com.dispatchflow.liveops.application.api;

import org.jspecify.annotations.NullMarked;
