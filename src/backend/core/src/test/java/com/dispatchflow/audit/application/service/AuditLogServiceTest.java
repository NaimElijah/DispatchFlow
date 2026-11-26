package com.dispatchflow.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.audit.application.api.AuditedKind;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.api.ClientKind;
import com.dispatchflow.audit.application.api.ClientRelease;
import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.audit.domain.AuditError.InvalidTraceId;
import com.dispatchflow.audit.domain.AuditError.MalformedErrorCode;
import com.dispatchflow.audit.domain.AuditError.MalformedOperationName;
import com.dispatchflow.audit.domain.AuditError.TooManyNamedRecords;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.ClientApp;
import com.dispatchflow.audit.domain.DeclaredApp;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.audit.domain.RecordKind;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The audit log (NFR-23, NFR-93, NFR-94, ADR-110): it translates the call into Audit's own record,
 * keeping only IDs, codes, enums, numbers and instants, and appends it. It runs against an
 * in-memory store; {@code AuditLogServiceIT} checks its transactions against the database.
 */
class AuditLogServiceTest {

    private static final Instant AT = Instant.parse("2027-06-13T07:30:00.123456Z");
    private static final AccountId USER = AccountId.of(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000a1"));
    private static final SessionId SESSION = SessionId.of(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000b1"));
    private static final PlanId PLAN = PlanId.of(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000c1"));
    private static final OrderId ORDER = OrderId.of(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000d1"));
    private static final String PUBLISH_PLAN = "publishPlan";
    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PLAN_HAS_VIOLATIONS = "PLAN_HAS_VIOLATIONS";

    private final AppendedAuditRecords appended = new AppendedAuditRecords();
    private final AuditLogService auditLog = new AuditLogService(appended);

    /** A dispatcher's call from the web app, version 1.4.2, signed in. */
    private static CallContext aSignedInCall() {
        return new CallContext(
                Optional.of(USER),
                Optional.of(SESSION),
                Optional.of(new ClientRelease(ClientKind.WEB, "1.4.2")),
                PUBLISH_PLAN,
                TRACE);
    }

    private static CallContext aCallFrom(ClientRelease client) {
        return new CallContext(Optional.of(USER), Optional.of(SESSION), Optional.of(client), PUBLISH_PLAN, TRACE);
    }

    private static CallContext aCallAs(String operation, String traceId) {
        return new CallContext(Optional.of(USER), Optional.of(SESSION), Optional.empty(), operation, traceId);
    }

    @Test
    void GivenASignedInCall_WhenSucceeded_ThenTheRecordHoldsItsUserSessionAppOperationRecordsAndTraceId() {
        auditLog.succeeded(aSignedInCall(), AT, List.of(AuditedRecord.of(PLAN), AuditedRecord.of(ORDER)));

        assertThat(appended.records())
                .containsExactly(new AuditRecord(
                        AT,
                        Optional.of(USER),
                        Optional.of(SESSION),
                        Optional.of(new DeclaredApp(ClientApp.WEB, new AppVersion(1, 4, 2))),
                        OperationName.of(PUBLISH_PLAN),
                        List.of(NamedRecord.of(PLAN), NamedRecord.of(ORDER)),
                        AuditOutcome.succeeded(),
                        TraceId.of(TRACE)));
    }

    @Test
    void GivenAServerStep_WhenSucceeded_ThenNoUserSessionOrAppIsRecorded() {
        auditLog.succeeded(CallContext.serverStep("purgeExpiredAuditRecords", TRACE), AT, List.of());

        assertThat(appended.records())
                .containsExactly(new AuditRecord(
                        AT,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        OperationName.of("purgeExpiredAuditRecords"),
                        List.of(),
                        AuditOutcome.succeeded(),
                        TraceId.of(TRACE)));
    }

    @ParameterizedTest
    @CsvSource({"WEB, WEB", "PHONE, PHONE"})
    void GivenEachClient_WhenSucceeded_ThenItsAppAndVersionAreRecorded(ClientKind kind, ClientApp app) {
        auditLog.succeeded(aCallFrom(new ClientRelease(kind, "3.0.12")), AT, List.of());

        assertThat(appended.only().app()).contains(new DeclaredApp(app, new AppVersion(3, 0, 12)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.4", "v1.4.2", "1.4.2-beta.1", "01.2.3", " 1.2.3", "2147483648.0.0", ""})
    void GivenAClientVersionThatDoesNotParse_WhenSucceeded_ThenTheAppIsLeftOut(String version) {
        auditLog.succeeded(
                aCallFrom(new ClientRelease(ClientKind.PHONE, version)), AT, List.of(AuditedRecord.of(PLAN)));

        AuditRecord record = appended.only();
        assertThat(record.app()).isEmpty();
        assertThat(record.user()).contains(USER);
        assertThat(record.records()).containsExactly(NamedRecord.of(PLAN));
    }

    @Test
    void GivenAClientVersionThatDoesNotParse_WhenRefused_ThenTheRefusalIsStillRecordedWithoutTheApp() {
        auditLog.refused(
                aCallFrom(new ClientRelease(ClientKind.WEB, "1.4.2-beta.1")), AT, List.of(), PLAN_HAS_VIOLATIONS);

        AuditRecord record = appended.only();
        assertThat(record.app()).isEmpty();
        assertThat(record.outcome()).isEqualTo(AuditOutcome.refused(ErrorCode.of(PLAN_HAS_VIOLATIONS)));
    }

    @Test
    void GivenARefusal_WhenRefused_ThenOnlyItsCodeIsKept() {
        auditLog.refused(aSignedInCall(), AT, List.of(AuditedRecord.of(PLAN)), PLAN_HAS_VIOLATIONS);

        assertThat(appended.records())
                .containsExactly(new AuditRecord(
                        AT,
                        Optional.of(USER),
                        Optional.of(SESSION),
                        Optional.of(new DeclaredApp(ClientApp.WEB, new AppVersion(1, 4, 2))),
                        OperationName.of(PUBLISH_PLAN),
                        List.of(NamedRecord.of(PLAN)),
                        AuditOutcome.refused(ErrorCode.of(PLAN_HAS_VIOLATIONS)),
                        TraceId.of(TRACE)));
    }

    @Test
    void GivenARefusedSignInThatKnowsNoUserOrSession_WhenRefused_ThenEachIsLeftOut() {
        CallContext signIn = new CallContext(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ClientRelease(ClientKind.PHONE, "2.0.0")),
                "signIn",
                TRACE);

        auditLog.refused(signIn, AT, List.of(), "ADDRESS_HAS_NO_ACCOUNT");

        AuditRecord record = appended.only();
        assertThat(record.user()).isEmpty();
        assertThat(record.session()).isEmpty();
        assertThat(record.outcome()).isEqualTo(AuditOutcome.refused(ErrorCode.of("ADDRESS_HAS_NO_ACCOUNT")));
    }

    @ParameterizedTest
    @MethodSource("malformedErrorCodes")
    void GivenAMalformedErrorCode_WhenRefused_ThenItFailsAsABugCausedByMalformedErrorCode(String errorCode) {
        assertABugCausedBy(() -> auditLog.refused(aSignedInCall(), AT, List.of(), errorCode), new MalformedErrorCode());
        assertThat(appended.records()).isEmpty();
    }

    static Stream<String> malformedErrorCodes() {
        return Stream.of("", "plan_has_violations", "PLAN HAS VIOLATIONS", "PLAN__VIOLATED", "A".repeat(101));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "publish plan", "1publishPlan", "publishPlan()"})
    void GivenAMalformedOperation_WhenSucceeded_ThenItFailsAsABugCausedByMalformedOperationName(String operation) {
        assertABugCausedBy(
                () -> auditLog.succeeded(aCallAs(operation, TRACE), AT, List.of()), new MalformedOperationName());
        assertThat(appended.records()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "00000000000000000000000000000000", "4BF92F3577B34DA6A3CE929D0E0E4736", "4bf92f35"})
    void GivenAnInvalidTraceId_WhenRefused_ThenItFailsAsABugCausedByInvalidTraceId(String traceId) {
        assertABugCausedBy(
                () -> auditLog.refused(aCallAs(PUBLISH_PLAN, traceId), AT, List.of(), PLAN_HAS_VIOLATIONS),
                new InvalidTraceId());
        assertThat(appended.records()).isEmpty();
    }

    @Test
    void GivenFiveHundredAndOneRecords_WhenSucceeded_ThenItFailsAsABugCausedByTooManyNamedRecords() {
        assertABugCausedBy(
                () -> auditLog.succeeded(aSignedInCall(), AT, stops(AuditRecord.MAX_RECORDS + 1)),
                new TooManyNamedRecords());
        assertThat(appended.records()).isEmpty();
    }

    @Test
    void GivenFiveHundredRecords_WhenSucceeded_ThenEachIsKeptInOrder() {
        List<AuditedRecord> named = stops(AuditRecord.MAX_RECORDS);

        auditLog.succeeded(aSignedInCall(), AT, named);

        assertThat(appended.only().records())
                .containsExactlyElementsOf(named.stream()
                        .map(record -> NamedRecord.of(StopId.of(record.id())))
                        .toList());
    }

    static Stream<Arguments> eachKernelId() {
        UUID id = UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000e1");
        return Stream.of(
                Arguments.of(AuditedRecord.of(AccountId.of(id)), NamedRecord.of(AccountId.of(id))),
                Arguments.of(AuditedRecord.of(SessionId.of(id)), NamedRecord.of(SessionId.of(id))),
                Arguments.of(AuditedRecord.of(DepotId.of(id)), NamedRecord.of(DepotId.of(id))),
                Arguments.of(AuditedRecord.of(VehicleId.of(id)), NamedRecord.of(VehicleId.of(id))),
                Arguments.of(AuditedRecord.of(CustomerId.of(id)), NamedRecord.of(CustomerId.of(id))),
                Arguments.of(AuditedRecord.of(OrderId.of(id)), NamedRecord.of(OrderId.of(id))),
                Arguments.of(AuditedRecord.of(ImportBatchId.of(id)), NamedRecord.of(ImportBatchId.of(id))),
                Arguments.of(AuditedRecord.of(PlanId.of(id)), NamedRecord.of(PlanId.of(id))),
                Arguments.of(AuditedRecord.of(RouteId.of(id)), NamedRecord.of(RouteId.of(id))),
                Arguments.of(AuditedRecord.of(StopId.of(id)), NamedRecord.of(StopId.of(id))),
                Arguments.of(AuditedRecord.of(JobId.of(id)), NamedRecord.of(JobId.of(id))),
                Arguments.of(AuditedRecord.of(ProposalId.of(id)), NamedRecord.of(ProposalId.of(id))),
                Arguments.of(AuditedRecord.of(AlertId.of(id)), NamedRecord.of(AlertId.of(id))),
                Arguments.of(AuditedRecord.of(RatingRequestId.of(id)), NamedRecord.of(RatingRequestId.of(id))));
    }

    @ParameterizedTest
    @MethodSource("eachKernelId")
    void GivenEachKernelIdNamed_WhenSucceeded_ThenTheRecordNamesItAsTheDomainDoes(
            AuditedRecord named, NamedRecord expected) {
        auditLog.succeeded(aSignedInCall(), AT, List.of(named));

        assertThat(appended.only().records()).containsExactly(expected);
    }

    @Test
    void GivenTheKindsTheApiNamesAndTheDomainKeeps_WhenListed_ThenTheyAreTheSameConstants() {
        assertThat(Arrays.stream(AuditedKind.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(RecordKind.values()).map(Enum::name).toList());
        assertThat(Arrays.stream(ClientKind.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ClientApp.values()).map(Enum::name).toList());
    }

    @Test
    void GivenTheNamedRecordsChangeAfterTheCall_WhenRead_ThenTheRecordKeepsItsOwnCopy() {
        List<AuditedRecord> named = new ArrayList<>(List.of(AuditedRecord.of(PLAN)));

        auditLog.succeeded(aSignedInCall(), AT, named);
        named.add(AuditedRecord.of(ORDER));

        assertThat(appended.only().records()).containsExactly(NamedRecord.of(PLAN));
    }

    @Test
    void GivenNoCall_WhenSucceeded_ThenItFailsLoudlyNamingIt() {
        assertThatThrownBy(() -> auditLog.succeeded(nothing(), AT, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("call");
        assertThat(appended.records()).isEmpty();
    }

    @Test
    void GivenNoTime_WhenRefused_ThenItFailsLoudlyNamingIt() {
        assertThatThrownBy(() -> auditLog.refused(aSignedInCall(), nothing(), List.of(), PLAN_HAS_VIOLATIONS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("at");
        assertThat(appended.records()).isEmpty();
    }

    @Test
    void GivenNoRecords_WhenSucceeded_ThenItFailsLoudlyNamingThem() {
        assertThatThrownBy(() -> auditLog.succeeded(aSignedInCall(), AT, nothing()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("records");
        assertThat(appended.records()).isEmpty();
    }

    @Test
    void GivenANullAmongTheRecords_WhenSucceeded_ThenItFailsLoudly() {
        List<AuditedRecord> named = Collections.singletonList(nothing());

        assertThatThrownBy(() -> auditLog.succeeded(aSignedInCall(), AT, named))
                .isInstanceOf(NullPointerException.class);
        assertThat(appended.records()).isEmpty();
    }

    @Test
    void GivenNoErrorCode_WhenRefused_ThenItFailsLoudlyNamingIt() {
        assertThatThrownBy(() -> auditLog.refused(aSignedInCall(), AT, List.of(), nothing()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("errorCode");
        assertThat(appended.records()).isEmpty();
    }

    /**
     * Every part of a record comes from the server, so Audit's refusal of one is a bug, thrown as
     * an {@code IllegalStateException} that no use case takes for its change's refusal (ADR-149).
     */
    private static void assertABugCausedBy(ThrowingCallable call, DomainError error) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalStateException.class)
                .cause()
                .isInstanceOfSatisfying(
                        RuleViolation.class,
                        refused -> assertThat(refused.error()).isEqualTo(error));
    }

    private static List<AuditedRecord> stops(int count) {
        return IntStream.range(0, count)
                .mapToObj(each -> AuditedRecord.of(StopId.of(new UUID(0x0193a4b2_7c1d_7e00L, each))))
                .toList();
    }

    // A null that the null-marked signature doesn't admit, to test the refusal itself.
    private static <T> T nothing() {
        return null;
    }
}
