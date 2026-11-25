package com.dispatchflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the asynchronous listeners always, and the scheduled jobs unless {@link
 * #SCHEDULING_ENABLED} is off (ADR-140 decision 10). Spring Boot turns on neither by itself:
 * without {@code @EnableAsync}, an {@code @Async} listener of ADR-129 would run inside the caller's
 * request with no error. The modules' listeners and jobs use this class and add no other.
 *
 * <p>The listeners run on Spring Boot's application executor, capped at {@link #ASYNC_THREADS},
 * with {@link MdcTaskDecorator} copying the caller's MDC, its trace ID included, to each. The proxy
 * is class-based, as Spring Boot's transaction proxy is, so an {@code @Async} bean needs no
 * interface.
 *
 * <p>The cap holds for the platform threads the core runs on. With {@code
 * spring.threads.virtual.enabled} on, Spring Boot builds an unpooled executor instead, whose
 * concurrency limit blocks the submitting thread when reached: the cap, and what happens past it,
 * must be decided again before that property is turned on.
 *
 * <p>The executor's queue stays unbounded (decided on 25 November 2026, ADR-142): a listener's task
 * waits rather than being refused. When a load test (NFR-10) shows the queue growing, bound it and
 * decide what a full queue does.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync(proxyTargetClass = true)
class SchedulingConfiguration {

    /** The property that turns the scheduled jobs off; the integration tests set it false. */
    static final String SCHEDULING_ENABLED = "dispatchflow.scheduling.enabled";

    /**
     * The tasks that run at once on Spring Boot's application executor: the asynchronous listeners,
     * and Spring MVC's asynchronous request processing, which shares that executor. Each listener
     * holds a database connection for its transaction, Hikari's pool holds 10 by default, and the
     * deployed VM has 4 vCPUs (TS §15), so 4 leave the requests their share of the pool.
     */
    static final int ASYNC_THREADS = 4;

    @Bean
    ThreadPoolTaskExecutorCustomizer asyncThreadsCapped() {
        return executor -> {
            executor.setCorePoolSize(ASYNC_THREADS);
            executor.setMaxPoolSize(ASYNC_THREADS);
        };
    }

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /** The scheduled jobs, behind their property. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnBooleanProperty(name = SCHEDULING_ENABLED, matchIfMissing = true)
    static class Schedules {}
}
