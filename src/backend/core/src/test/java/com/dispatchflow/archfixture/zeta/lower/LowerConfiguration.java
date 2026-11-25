package com.dispatchflow.archfixture.zeta.lower;

import com.dispatchflow.archfixture.zeta.lower.adapter.in.web.LowerForm;
import com.dispatchflow.archfixture.zeta.lower.domain.LowerDomainUsesTheClock;
import org.springframework.context.annotation.Import;

/**
 * Rule 15: a configuration that imports its own module's adapter and domain classes, which lie in
 * its module but outside its {@code application.service}. It carries no {@code @Configuration}, so
 * no context that scans the test tree reads it.
 */
@Import({LowerForm.class, LowerDomainUsesTheClock.class})
class LowerConfiguration {}
