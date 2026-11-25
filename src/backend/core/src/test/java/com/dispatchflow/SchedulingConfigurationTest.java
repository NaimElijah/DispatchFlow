package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

class SchedulingConfigurationTest {

    private static final String TRACE_ID = "traceId";
    private static final String CALLERS = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String WORKERS = "00f067aa0ba902b700f067aa0ba902b7";
    private static final long WAIT_SECONDS = 10;
    private static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    private static final String TASK_SCHEDULER = "taskScheduler";
    // Spring Boot's default thread-name prefix for its application executor.
    private static final String APPLICATION_EXECUTOR_THREADS = "task-";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfiguration.class);

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @Test
    void GivenAnAsyncListenerBesideTheScheduler_WhenItRuns_ThenItRunsOnTheApplicationExecutorWithTheCallersMdc() {
        // The scheduler is a task executor too; the listener must still run on the capped one.
        runner.withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withBean(AsyncListener.class)
                .run(context -> {
                    assertThat(context).hasBean(TASK_SCHEDULER);
                    MDC.put(TRACE_ID, CALLERS);

                    context.publishEvent(new Happened());

                    Heard heard = context.getBean(AsyncListener.class).heard().get(WAIT_SECONDS, TimeUnit.SECONDS);
                    assertThat(heard.thread()).isNotEqualTo(Thread.currentThread());
                    assertThat(heard.thread().getName()).startsWith(APPLICATION_EXECUTOR_THREADS);
                    assertThat(heard.mdc()).containsEntry(TRACE_ID, CALLERS);
                });
    }

    @Test
    void GivenAWorkerWithAnMdcOfItsOwn_WhenADecoratedTaskRunsOnIt_ThenTheWorkersMdcIsRestored() {
        MDC.put(TRACE_ID, CALLERS);
        CompletableFuture<@Nullable Map<String, String>> seen = new CompletableFuture<>();
        Runnable decorated = new MdcTaskDecorator().decorate(() -> seen.complete(MDC.getCopyOfContextMap()));
        MDC.put(TRACE_ID, WORKERS);

        decorated.run();

        assertThat(seen.join()).containsEntry(TRACE_ID, CALLERS);
        assertThat(MDC.get(TRACE_ID)).isEqualTo(WORKERS);
    }

    @Test
    void GivenACallerWithNoMdc_WhenADecoratedTaskRunsOnAWorkerWithOne_ThenTheTaskSeesNoneAndTheWorkersIsRestored() {
        CompletableFuture<@Nullable String> seen = new CompletableFuture<>();
        Runnable decorated = new MdcTaskDecorator().decorate(() -> seen.complete(MDC.get(TRACE_ID)));
        MDC.put(TRACE_ID, WORKERS);

        decorated.run();

        assertThat(seen.join()).isNull();
        assertThat(MDC.get(TRACE_ID)).isEqualTo(WORKERS);
    }

    @Test
    void GivenNoProperty_WhenTheContextStarts_ThenSchedulingIsOn() {
        runner.run(context ->
                assertThat(context).hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void GivenTheSchedulingPropertyOff_WhenTheContextStarts_ThenSchedulingIsOffAndAsyncStillOn() {
        runner.withPropertyValues(SchedulingConfiguration.SCHEDULING_ENABLED + "=false")
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
                    assertThat(context).hasBean(TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME);
                });
    }

    @Test
    void GivenPlatformThreads_WhenTheExecutorIsBuilt_ThenItsPoolIsTheNamedLimit() {
        runner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean(APPLICATION_TASK_EXECUTOR, ThreadPoolTaskExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(SchedulingConfiguration.ASYNC_THREADS);
            assertThat(executor.getMaxPoolSize()).isEqualTo(SchedulingConfiguration.ASYNC_THREADS);
        });
    }

    record Happened() {}

    record Heard(Thread thread, @Nullable Map<String, String> mdc) {}

    /**
     * An asynchronous listener, as a module's listener adapter is (ADR-129). The test reads what it
     * heard through a method, because the bean is a class-based proxy whose own fields are unset.
     */
    static class AsyncListener {

        private final CompletableFuture<Heard> heard = new CompletableFuture<>();

        public CompletableFuture<Heard> heard() {
            return heard;
        }

        @Async
        @EventListener
        public void on(Happened event) {
            heard.complete(new Heard(Thread.currentThread(), MDC.getCopyOfContextMap()));
        }
    }
}
