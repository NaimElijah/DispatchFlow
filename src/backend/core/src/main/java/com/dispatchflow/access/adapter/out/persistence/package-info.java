/**
 * Access's persistence adapter (ADR-126, ADR-127): the JPA implementations of the repositories of
 * {@code UserAccount}, {@code Session} and {@code CompanyProfile}, over the tables of the {@code
 * access} schema, and the auto-applied converters of Access's single-column values.
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
