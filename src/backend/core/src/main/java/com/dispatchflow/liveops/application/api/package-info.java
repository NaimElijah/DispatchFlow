/**
 * Live Operations' published API (DL §2.2, §2.3 rule 4): {@link
 * com.dispatchflow.liveops.application.api.RaiseAlert}, which Messaging calls on a low rating,
 * and {@link com.dispatchflow.liveops.application.api.ArrivalProjections}, the projections and the
 * progress that Messaging's minute job and listeners ask. Reporting reads no Java API of Live
 * Operations': it reads the views Live Operations publishes (ADR-041, ADR-109).
 *
 * <p>Its types use only kernel types, JDK types, their own records, enums and sealed interfaces,
 * and Audit's {@code CallContext}, never a domain type (DL §2.3 rule 1). The caller translates each
 * answer into its own values.
 */
@NullMarked
package com.dispatchflow.liveops.application.api;

import org.jspecify.annotations.NullMarked;
