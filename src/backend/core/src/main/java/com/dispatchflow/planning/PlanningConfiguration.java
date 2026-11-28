package com.dispatchflow.planning;

import com.dispatchflow.planning.application.service.plan.OpenPlan;
import com.dispatchflow.planning.application.service.plan.OpenPlanChange;
import com.dispatchflow.planning.application.service.plan.RequestSolve;
import com.dispatchflow.planning.application.service.plan.RequestSolveChange;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Makes Planning's use cases beans, TS §2.1's configuration layer (ADR-140 decision 10). A use case
 * and its {@code Change} join this {@code @Import} list in the pull request that builds its body,
 * so a throwing body is never wired (ADR-119 decision 4). {@code @Import} lets Spring call each
 * constructor, so none is written twice: no {@code @Bean} method and no scan. It stays
 * package-private and lists only this module's {@code application.service} classes ({@code
 * ApplicationRules} rule 15).
 */
@Configuration(proxyBeanMethods = false)
@Import({OpenPlan.class, OpenPlanChange.class, RequestSolve.class, RequestSolveChange.class})
class PlanningConfiguration {}
