package com.dispatchflow.audit.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.audit.domain.AuditError.TooManyNamedRecords;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.SessionId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The audit record (NFR-23, NFR-93, NFR-94, ADR-110): exactly NFR-93's fields, each an ID, a code,
 * an enum, a number or an instant, and its one-year retention (NFR-258, ADR-110 point 2).
 */
class AuditRecordTest {

    private static final Instant AT = Instant.parse("2027-03-10T10:00:00Z");
    private static final AccountId USER = AccountId.of(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000a1"));
    private static final SessionId SESSION = new SessionId(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000b1"));
    private static final DeclaredApp PHONE_1_4_2 = new DeclaredApp(ClientApp.PHONE, new AppVersion(1, 4, 2));
    private static final OperationName PUBLISH_PLAN = OperationName.of("publishPlan");
    private static final NamedRecord PLAN =
            NamedRecord.of(new PlanId(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000c1")));
    private static final NamedRecord ORDER =
            NamedRecord.of(new OrderId(UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000d1")));
    private static final TraceId TRACE = TraceId.of("4bf92f3577b34da6a3ce929d0e0e4736");

    /** A dispatcher's published plan, with every field known. */
    private static AuditRecord aPublishedPlanRecordAt(Instant at) {
        return new AuditRecord(
                at,
                Optional.of(USER),
                Optional.of(SESSION),
                Optional.of(PHONE_1_4_2),
                PUBLISH_PLAN,
                List.of(PLAN, ORDER),
                AuditOutcome.succeeded(),
                TRACE);
    }

    @Test
    void GivenEveryFieldKnown_WhenRecorded_ThenTheRecordHoldsEachAsGiven() {
        AuditRecord record = aPublishedPlanRecordAt(AT);

        assertThat(record.at()).isEqualTo(Instant.parse("2027-03-10T10:00:00Z"));
        assertThat(record.user()).contains(USER);
        assertThat(record.session()).contains(SESSION);
        assertThat(record.app()).contains(new DeclaredApp(ClientApp.PHONE, new AppVersion(1, 4, 2)));
        assertThat(record.operation()).isEqualTo(OperationName.of("publishPlan"));
        assertThat(record.records()).containsExactly(PLAN, ORDER);
        assertThat(record.outcome()).isEqualTo(new AuditOutcome.Succeeded());
        assertThat(record.traceId()).isEqualTo(TraceId.of("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void GivenARefusedSignInWithNoKnownUserSessionOrRecord_WhenRecorded_ThenItIsAuditedWithItsCode() {
        AuditRecord record = new AuditRecord(
                AT,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new DeclaredApp(ClientApp.WEB, new AppVersion(2, 0, 0))),
                OperationName.of("signIn"),
                List.of(),
                AuditOutcome.refused(ErrorCode.of("SIGN_IN_REFUSED")),
                TRACE);

        assertThat(record.user()).isEmpty();
        assertThat(record.session()).isEmpty();
        assertThat(record.records()).isEmpty();
        assertThat(record.outcome()).isEqualTo(new AuditOutcome.Refused(ErrorCode.of("SIGN_IN_REFUSED")));
    }

    @Test
    void GivenAServerStepWithNoClient_WhenRecorded_ThenNoAppIsDeclared() {
        AuditRecord record = new AuditRecord(
                AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OperationName.of("deactivateAdminsByWrittenRequest"),
                List.of(NamedRecord.of(USER)),
                AuditOutcome.succeeded(),
                TRACE);

        assertThat(record.app()).isEmpty();
    }

    @Test
    void GivenASessionWhoseUserIsNotKnown_WhenRecorded_ThenEachIsKeptAsKnown() {
        AuditRecord record = new AuditRecord(
                AT,
                Optional.empty(),
                Optional.of(SESSION),
                Optional.of(PHONE_1_4_2),
                OperationName.of("refreshSession"),
                List.of(),
                AuditOutcome.refused(ErrorCode.of("REFRESH_REFUSED")),
                TRACE);

        assertThat(record.user()).isEmpty();
        assertThat(record.session()).contains(SESSION);
    }

    @Test
    void GivenTheNamedRecordsChangeAfterRecording_WhenRead_ThenTheRecordKeepsItsOwnCopyInOrder() {
        List<NamedRecord> named = new ArrayList<>(List.of(ORDER, PLAN));
        AuditRecord record = new AuditRecord(
                AT,
                Optional.of(USER),
                Optional.of(SESSION),
                Optional.of(PHONE_1_4_2),
                PUBLISH_PLAN,
                named,
                AuditOutcome.succeeded(),
                TRACE);

        named.clear();

        assertThat(record.records()).containsExactly(ORDER, PLAN);
        assertThatThrownBy(() -> record.records().add(PLAN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void GivenNfr93_WhenTheRecordsComponentsAreListed_ThenTheyAreExactlyItsFields() {
        assertThat(Arrays.stream(AuditRecord.class.getRecordComponents())
                        .map(component -> component.getName() + ":"
                                + component.getGenericType().getTypeName()))
                .containsExactly(
                        "at:java.time.Instant",
                        "user:java.util.Optional<com.dispatchflow.shared.AccountId>",
                        "session:java.util.Optional<com.dispatchflow.shared.SessionId>",
                        "app:java.util.Optional<com.dispatchflow.audit.domain.DeclaredApp>",
                        "operation:com.dispatchflow.audit.domain.OperationName",
                        "records:java.util.List<com.dispatchflow.audit.domain.NamedRecord>",
                        "outcome:com.dispatchflow.audit.domain.AuditOutcome",
                        "traceId:com.dispatchflow.audit.domain.TraceId");
    }

    @ParameterizedTest(name = "{0} records")
    @ValueSource(ints = {0, 1, 499, 500})
    void GivenUpToFiveHundredNamedRecords_WhenRecorded_ThenEachIsKept(int count) {
        Fields fields = new Fields();
        fields.records = Collections.nCopies(count, ORDER);

        assertThat(fields.build().records()).hasSize(count);
    }

    @ParameterizedTest(name = "{0} records")
    @ValueSource(ints = {501, 10_000})
    void GivenMoreThanFiveHundredNamedRecords_WhenRecorded_ThenTooManyNamedRecords(int count) {
        Fields fields = new Fields();
        fields.records = Collections.nCopies(count, ORDER);

        assertRefused(fields::build, new TooManyNamedRecords());
    }

    static Stream<Arguments> eachComponentMissing() {
        return Stream.of(
                Arguments.of("at", (Consumer<Fields>) fields -> fields.at = null),
                Arguments.of("user", (Consumer<Fields>) fields -> fields.user = null),
                Arguments.of("session", (Consumer<Fields>) fields -> fields.session = null),
                Arguments.of("app", (Consumer<Fields>) fields -> fields.app = null),
                Arguments.of("operation", (Consumer<Fields>) fields -> fields.operation = null),
                Arguments.of("records", (Consumer<Fields>) fields -> fields.records = null),
                Arguments.of("outcome", (Consumer<Fields>) fields -> fields.outcome = null),
                Arguments.of("traceId", (Consumer<Fields>) fields -> fields.traceId = null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eachComponentMissing")
    void GivenAMissingComponent_WhenRecorded_ThenItFailsLoudlyNamingIt(String component, Consumer<Fields> missing) {
        Fields fields = new Fields();
        missing.accept(fields);

        assertThatThrownBy(fields::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessage(component);
    }

    @Test
    void GivenANullAmongTheNamedRecords_WhenRecorded_ThenItFailsLoudly() {
        Fields fields = new Fields();
        fields.records = new ArrayList<>();
        fields.records.add(null);

        assertThatThrownBy(fields::build).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource({
        // Recorded on 10 March 2027, a year earlier, in standard time on both dates.
        "2027-03-10T10:00:00Z, 2028-03-10T09:59:59Z, false",
        "2027-03-10T10:00:00Z, 2028-03-10T10:00:00Z, false",
        "2027-03-10T10:00:00Z, 2028-03-10T10:00:00.000000001Z, true",
        // 25 March 2027 is in standard time and 25 March 2028 in summer time: a year is counted on
        // Israel's calendar, so noon local is 10:00Z when recorded and 09:00Z a year later.
        "2027-03-25T10:00:00Z, 2028-03-25T08:59:59Z, false",
        "2027-03-25T10:00:00Z, 2028-03-25T09:00:00Z, false",
        "2027-03-25T10:00:00Z, 2028-03-25T09:00:00.000000001Z, true",
        // Recorded on 29 February 2028: a year later is 28 February 2029.
        "2028-02-29T10:00:00Z, 2029-02-28T10:00:00Z, false",
        "2028-02-29T10:00:00Z, 2029-02-28T10:00:00.000000001Z, true"
    })
    void GivenARecordAndTheRetentionJobsTime_WhenAskedWhetherItIsPastRetention_ThenOnlyOneOlderThanAYearIs(
            Instant at, Instant now, boolean expected) {
        assertThat(aPublishedPlanRecordAt(at).isPastRetention(now)).isEqualTo(expected);
    }

    @Test
    void GivenNoTimeForTheRetentionCheck_WhenAsked_ThenItFailsLoudly() {
        assertThatThrownBy(() -> aPublishedPlanRecordAt(AT).isPastRetention(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }

    /** The components of a complete record, any of which a test may blank. */
    private static final class Fields {
        Instant at = AT;
        Optional<AccountId> user = Optional.of(USER);
        Optional<SessionId> session = Optional.of(SESSION);
        Optional<DeclaredApp> app = Optional.of(PHONE_1_4_2);
        OperationName operation = PUBLISH_PLAN;
        List<NamedRecord> records = List.of(PLAN);
        AuditOutcome outcome = AuditOutcome.succeeded();
        TraceId traceId = TRACE;

        AuditRecord build() {
            return new AuditRecord(at, user, session, app, operation, records, outcome, traceId);
        }
    }
}
