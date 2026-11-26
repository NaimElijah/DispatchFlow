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
        // Recorded on 29 February 2028: kept through 28 February 2029, and past its retention from
        // 1 March 2029 at 00:00 Israel time, 22:00Z the day before (ADR-149 decision 5).
        "2028-02-29T10:00:00Z, 2029-02-28T10:00:00.000000001Z, false",
        "2028-02-29T10:00:00Z, 2029-02-28T21:59:59.999999999Z, false",
        "2028-02-29T10:00:00Z, 2029-02-28T22:00:00Z, true"
    })
    void GivenARecordAndTheRetentionJobsTime_WhenAskedWhetherItIsPastRetention_ThenOnlyOneOlderThanAYearIs(
            Instant at, Instant now, boolean expected) {
        assertThat(aPublishedPlanRecordAt(at).isPastRetention(now)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        // 30 October 2028 is in standard time and 30 October 2027 in summer time: noon local is
        // 10:00Z now and 09:00Z a year earlier.
        "2028-10-30T10:00:00Z, 2027-10-30T09:00:00Z",
        // 25 March 2028 is in summer time and 25 March 2027 in standard time.
        "2028-03-25T09:00:00Z, 2027-03-25T10:00:00Z",
        // 02:30 on 26 March 2027 never happened: the clocks skipped from 02:00 to 03:00. Read with
        // the summer offset, it is the earlier of the two instants it could stand for.
        "2028-03-25T23:30:00Z, 2027-03-25T23:30:00Z",
        // 01:30 on 25 October 2026 happened twice, in summer time and then in standard time: the
        // cutoff is the first, asked from summer time...
        "2027-10-24T22:30:00Z, 2026-10-24T22:30:00Z",
        // ...and from standard time: 01:30 on 31 October 2027 happened twice too.
        "2028-10-30T23:30:00Z, 2027-10-30T22:30:00Z",
        // 29 February 2028 a year earlier is the last day of February 2027.
        "2028-02-29T08:00:00Z, 2027-02-28T08:00:00Z",
        // Midnight of 1 March 2029 a year earlier is midnight of 1 March 2028.
        "2029-02-28T22:00:00Z, 2028-02-29T22:00:00Z",
        // A time's microseconds and nanoseconds are kept as given.
        "2027-06-13T07:30:00.123456789Z, 2026-06-13T07:30:00.123456789Z"
    })
    void GivenTheTimeAsked_WhenTheRetentionCutoffIsComputed_ThenItIsTheSameIsraelLocalTimeAYearEarlier(
            Instant now, Instant expected) {
        assertThat(AuditRecord.retentionCutoff(now)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"2029-02-28T09:30:00Z", "2029-02-28T10:00:00Z", "2029-02-28T22:00:00Z", "2029-03-01T10:00:00Z"})
    void GivenARecordAndANewerOne_WhenAskedAtAnyTime_ThenTheNewerIsPastItsRetentionOnlyOnceTheOlderIs(Instant now) {
        // A "year after each record" rule would delete this newer record first, at 09:30Z on 28
        // February 2029, while it kept the older one, so that no cutoff could match it (ADR-149).
        AuditRecord older = aPublishedPlanRecordAt(Instant.parse("2028-02-28T10:00:01Z"));
        AuditRecord newer = aPublishedPlanRecordAt(Instant.parse("2028-02-29T09:00:00Z"));

        assertThat(!newer.isPastRetention(now) || older.isPastRetention(now))
                .as("the newer record is past its retention only once the older one is")
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        // Made at 01:30 summer time on 25 October 2026, the first pass of the repeated hour, and
        // asked at 01:00 summer time on 25 October 2027, half an hour before its year is up.
        "2026-10-24T22:30:00Z, 2027-10-24T22:00:00Z",
        // Made at exactly that local time, and asked as its year is up.
        "2026-10-24T22:30:00Z, 2027-10-24T22:30:00Z",
        // Made at 03:15 on 27 March 2026, just after the clocks skipped an hour, and asked at 02:30
        // on 27 March 2027, which reads back into the skipped hour.
        "2026-03-27T00:15:00Z, 2027-03-26T23:30:00Z"
    })
    void GivenARecordMadeNearAClockChange_WhenAskedBeforeItsLocalYearIsUp_ThenItIsKept(Instant at, Instant now) {
        assertThat(aPublishedPlanRecordAt(at).isPastRetention(now)).isFalse();
    }

    @Test
    void GivenNoTimeForTheRetentionCutoff_WhenComputed_ThenItFailsLoudly() {
        assertThatThrownBy(() -> AuditRecord.retentionCutoff(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
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
