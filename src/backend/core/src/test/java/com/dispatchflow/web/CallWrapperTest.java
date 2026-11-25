package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.PessimisticLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * The call wrapper in Spring Boot's own MVC configuration, with {@code web}'s beans and no
 * database: the retry re-runs the handler with the arguments already read, and reads whether a try
 * committed from a transaction manager that has {@link CommitWatch} as its listener.
 */
class CallWrapperTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final String NOTE = "{\"text\":\"leave at the door\"}";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    ValidationAutoConfiguration.class,
                    WebMvcAutoConfiguration.class))
            .withBean(CallWrapper.class)
            .withBean(CallRetry.class)
            .withBean(CommitWatch.class)
            .withBean(ServedJson.class)
            .withBean(ContractErrorHandler.class)
            .withBean(BodySizeLimit.class)
            .withBean(KernelErrorHandler.class)
            .withBean(UnexpectedErrorHandler.class)
            .withBean(TraceIdFilter.class)
            .withBean(Clock.class, CallRetryTest.FixedClock::new)
            .withBean(AuditLog.class, CallRetryTest.RecordingAuditLog::new)
            .withBean(StubTransactionManager.class, StubTransactionManager::new)
            .withBean(RacingController.class);

    private void run(Consumer<Fixture> test) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            test.accept(new Fixture(context));
        });
    }

    private record Fixture(AssertableWebApplicationContext context) {

        MockMvcTester mvc() {
            return MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean(TraceIdFilter.class))
                    .build());
        }

        RacingController controller() {
            RacingController controller = context.getBean(RacingController.class);
            StubTransactionManager transactions = context.getBean(StubTransactionManager.class);
            transactions.setTransactionExecutionListeners(List.of(context.getBean(CommitWatch.class)));
            controller.transactions = transactions;
            return controller;
        }

        MvcTestResult post(String path, HttpHeaders headers) {
            return mvc().post()
                    .uri(path)
                    .headers(sent -> sent.addAll(headers))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(NOTE)
                    .exchange();
        }
    }

    @Test
    void GivenBootsMvcConfiguration_WhenTheAdapterIsRead_ThenItIsTheCallWrappersRetryingAdapter() {
        run(fixture -> assertThat(fixture.context().getBean(RequestMappingHandlerAdapter.class))
                .isInstanceOf(CallWrapper.RetryingAdapter.class));
    }

    @Test
    void GivenAHandlerThatTakesABody_WhenTheCallIsRetried_ThenItGetsTheArgumentsItAlreadyRead() {
        run(fixture -> {
            RacingController controller = fixture.controller();
            controller.conflicts = 2;

            MvcTestResult result = fixture.post(RacingController.BODY, new HttpHeaders());

            assertThat(result).hasStatusOk();
            assertThat(controller.tries).hasValue(3);
            assertThat(controller.notes).hasSize(3);
            assertThat(controller.notes).allSatisfy(note -> assertThat(note).isSameAs(controller.notes.getFirst()));
        });
    }

    @Test
    void GivenAStaleVersionCausedByALockWait_WhenACallWithAVersionRuns_Then412() {
        run(fixture -> {
            RacingController controller = fixture.controller();
            HttpHeaders ifMatch = new HttpHeaders();
            ifMatch.setIfMatch("\"3\"");

            MvcTestResult result = fixture.post(RacingController.STALE_AFTER_LOCK_WAIT, ifMatch);

            assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
            assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(StaleVersion.CODE);
            assertThat(controller.tries).hasValue(1);
            assertThat(fixture.context().getBean(CallRetryTest.RecordingAuditLog.class).refusals)
                    .singleElement()
                    .satisfies(refusal -> {
                        assertThat(refusal.errorCode()).isEqualTo(StaleVersion.CODE);
                        assertThat(refusal.operation()).isEqualTo("staleAfterLockWait");
                        assertThat(refusal.traceId()).hasSize(32);
                    });
        });
    }

    @Test
    void GivenATryWhoseFirstTransactionCommitted_WhenALaterStepFailsALockWait_ThenItIsNotRetriedAndAnswers500() {
        run(fixture -> {
            RacingController controller = fixture.controller();

            MvcTestResult result = fixture.post(RacingController.COMMIT_THEN_LOCK_WAIT, new HttpHeaders());

            // The change is stored, so a 503 would invite the client to send it again (ADR-142).
            assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNEXPECTED_ERROR);
            assertThat(controller.tries).hasValue(1);
            assertThat(fixture.context().getBean(CallRetryTest.RecordingAuditLog.class).refusals)
                    .isEmpty();
        });
    }

    @Test
    void GivenFourConflicts_WhenACallWithoutAVersionRuns_Then503WithRetryAfterAfterFourTries() {
        run(fixture -> {
            RacingController controller = fixture.controller();
            controller.conflicts = 1 + CallRetry.MORE_TRIES;

            MvcTestResult result = fixture.post(RacingController.BODY, new HttpHeaders());

            assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.TRY_AGAIN_LATER);
            assertThat(result).headers().hasValue(HttpHeaders.RETRY_AFTER, "2");
            assertThat(controller.tries).hasValue(4);
            assertThat(fixture.context().getBean(CallRetryTest.RecordingAuditLog.class).refusals)
                    .singleElement()
                    .satisfies(refusal -> assertThat(refusal.errorCode()).isEqualTo(WebCodes.TRY_AGAIN_LATER));
        });
    }

    @Test
    void GivenATryWhoseOnlyCommitWasReadOnly_WhenALaterStepFailsALockWait_ThenItIsRetried() {
        run(fixture -> {
            RacingController controller = fixture.controller();

            MvcTestResult result = fixture.post(RacingController.READ_ONLY_COMMIT_THEN_LOCK_WAIT, new HttpHeaders());

            assertThat(result).hasStatusOk();
            assertThat(controller.tries).hasValue(2);
        });
    }

    /** A note the client sends; the handler keeps each instance it is given. */
    record Note(String text) {}

    /** A handler whose tries the tests count. */
    @RestController
    static class RacingController {

        static final String BODY = "/call-wrapper-test/body";
        static final String STALE_AFTER_LOCK_WAIT = "/call-wrapper-test/stale-after-lock-wait";
        static final String COMMIT_THEN_LOCK_WAIT = "/call-wrapper-test/commit-then-lock-wait";
        static final String READ_ONLY_COMMIT_THEN_LOCK_WAIT = "/call-wrapper-test/read-only-commit-then-lock-wait";

        final AtomicInteger tries = new AtomicInteger();
        final List<Note> notes = new ArrayList<>();
        int conflicts;
        AbstractPlatformTransactionManager transactions;

        @PostMapping(BODY)
        String body(@RequestBody Note note) {
            notes.add(note);
            if (tries.incrementAndGet() <= conflicts) {
                throw new StaleVersion("Plan", ID, new IllegalStateException("optimistic lock"));
            }
            return note.text();
        }

        @PostMapping(STALE_AFTER_LOCK_WAIT)
        String staleAfterLockWait(@RequestBody Note note) {
            tries.incrementAndGet();
            throw new StaleVersion("Plan", ID, new PessimisticLockException("lock wait"));
        }

        @PostMapping(COMMIT_THEN_LOCK_WAIT)
        String commitThenLockWait(@RequestBody Note note) {
            tries.incrementAndGet();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {});
            throw new PessimisticLockException("lock wait");
        }

        @PostMapping(READ_ONLY_COMMIT_THEN_LOCK_WAIT)
        String readOnlyCommitThenLockWait(@RequestBody Note note) {
            TransactionTemplate readOnly = new TransactionTemplate(transactions);
            readOnly.setReadOnly(true);
            readOnly.executeWithoutResult(status -> {});
            if (tries.incrementAndGet() == 1) {
                throw new PessimisticLockException("lock wait");
            }
            return note.text();
        }
    }

    /** A transaction manager with no resource, whose commits only notify its listeners. */
    static final class StubTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No resource to bind: only the listeners matter here.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // Nothing to commit.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // Nothing to roll back.
        }
    }
}
