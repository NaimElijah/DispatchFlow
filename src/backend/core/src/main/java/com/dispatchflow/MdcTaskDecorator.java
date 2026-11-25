package com.dispatchflow;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the submitting thread's MDC, its trace ID included, to the thread that runs an
 * asynchronous listener, so the listener's log lines carry the request's trace ID (ADR-140 decision
 * 8). The worker's own MDC is put back afterwards, so a pooled thread carries nothing from one task
 * to the next.
 */
class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        @Nullable Map<String, String> callers = MDC.getCopyOfContextMap();
        return () -> {
            @Nullable Map<String, String> workers = MDC.getCopyOfContextMap();
            setOrClear(callers);
            try {
                task.run();
            } finally {
                setOrClear(workers);
            }
        };
    }

    private static void setOrClear(@Nullable Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
