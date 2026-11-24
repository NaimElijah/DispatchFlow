/**
 * Access's persistence adapter (ADR-126, ADR-127): the JPA implementations of the repositories of
 * {@code UserAccount}, {@code Session} and {@code CompanyProfile}, over the tables of the {@code
 * access} schema, and the auto-applied converters of Access's single-column values.
 *
 * <p>It will also implement the ports ADR-130 decision 4 gives it, which no class implements yet:
 * {@code AccessViews} and {@code PushTokens}; {@code RevocationCache}, because the repositories
 * answer the token check through its loader, and an eviction is sent on to every other core process
 * (ADR-131); and {@code RecoveryEmailCap}, which counts the Israel day's recovery links under a
 * lock keyed by the day (ADR-131).
 *
 * <p>Hibernate reads the registration below when it scans this package, so a stored {@code
 * DefaultWindow} is rebuilt through {@code StoredStateInvalid.whileRestoring}. {@code
 * EmailSetByAnother} can't refuse a stored value, and the migration's {@code CHECK} keeps its
 * columns whole, so Hibernate builds it through its canonical constructor and needs none.
 */
@NullMarked
@EmbeddableInstantiatorRegistration(
        embeddableClass = DefaultWindow.class,
        instantiator = DefaultWindowInstantiator.class)
package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.companyprofile.DefaultWindow;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.jspecify.annotations.NullMarked;
