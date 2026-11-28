package com.dispatchflow.planning.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.DatabaseOwner;
import com.dispatchflow.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Opening a date's plan and requesting its solve through the generated {@code PlanningPlansApi},
 * as the stand-in Dispatcher, against PostgreSQL (#184, #161): the contract's validation, the
 * statuses and headers, and what the calls store.
 *
 * <p>Every test opens its plan for a date of its own, from {@link #FIRST_DATE}, far from the other
 * tests' dates and from any today the core's real clock reads, since the database is shared and
 * never cleaned. The daily lane is shared too, and refuses a sixth queued job (BR-112), so each test
 * starts and ends by ending every unfinished daily job, its own and those other test classes left,
 * as {@code SolveJobPersistenceIT} does inside its transactions: here the calls commit, so the
 * owner does it, as test damage goes (ADR-145 decision 8).
 */
@IntegrationTest
class PlanControllerIT {

    private static final LocalDate FIRST_DATE = LocalDate.of(9000, 1, 1);
    private static final String TRACE_OF_THE_OPENING = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final String NO_PLAN = "0194d6f0-0000-7000-8000-00000000dead";
    private static final long QUEUED_KEYS = 0x0194d6f0_0000_7000L;
    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");
    private static final String PLANS = "/api/v1/plans";
    private static final String NO_STORE_NO_TRANSFORM = "no-store, no-transform";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    @DatabaseOwner
    private JdbcClient owner;

    @BeforeEach
    void emptyTheDailyLaneFirst() {
        endEveryUnfinishedDailyJob();
    }

    @AfterEach
    void leaveTheDailyLaneEmpty() {
        endEveryUnfinishedDailyJob();
    }

    // --- openPlan

    @Test
    void GivenADateWithNoPlan_WhenOpenPlan_Then201WithLocationAndETag() {
        MvcTestResult result = open(dateOf(1));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String plan = read(result, "$.planId");
        assertThat(plan).matches(UUID_PATTERN);
        assertThat(result).headers().hasValue(HttpHeaders.LOCATION, PLANS + "/" + plan);
        assertThat(result).headers().hasValue(HttpHeaders.ETAG, "\"0\"");
        assertThat(storedPlan(plan))
                .containsEntry("status", "DRAFT")
                .containsEntry("delivery_date", dateOf(1).toString());
    }

    @Test
    void GivenAPastDate_WhenOpenPlan_Then422DateInPast() {
        LocalDate yesterday = LocalDate.now(ISRAEL).minusDays(1);

        MvcTestResult result = open(yesterday);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("DATE_IN_PAST");
        assertThat(result).bodyJson().extractingPath("$.params.date").asString().isEqualTo(yesterday.toString());
    }

    @Test
    void GivenADateWithAPlan_WhenOpenPlan_Then422PlanAlreadyExistsNamingIt() {
        String first = planIdOf(open(dateOf(2)));

        MvcTestResult result = open(dateOf(2));

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("PLAN_ALREADY_EXISTS");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.params.existing")
                .asString()
                .isEqualTo(first);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.params.date")
                .asString()
                .isEqualTo(dateOf(2).toString());
        assertThat(recordedOutcomes(traceIdOf(result))).containsExactly("REFUSED PLAN_ALREADY_EXISTS");
    }

    @Test
    void GivenAYearPast9999_WhenOpenPlan_Then422DateOutOfRangeAndItsRefusalIsRecorded() {
        MvcTestResult result = mvc.post()
                .uri(PLANS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"+10000-01-01\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("DATE_OUT_OF_RANGE");
        assertThat(recordedOutcomes(traceIdOf(result))).containsExactly("REFUSED DATE_OUT_OF_RANGE");
    }

    @Test
    void GivenNoDate_WhenOpenPlan_Then400Required() {
        MvcTestResult result = mvc.post()
                .uri(PLANS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo("REQUIRED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/date");
    }

    @Test
    void GivenAPropertyTheContractDoesNotDeclare_WhenOpenPlan_Then400UnknownProperty() {
        MvcTestResult result = mvc.post()
                .uri(PLANS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + dateOf(3) + "\",\"solver\":\"pyvrp\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo("UNKNOWN_PROPERTY");
    }

    /**
     * The operation is the generated method's name, which the audit record keeps (ADR-140), and
     * the record names the plan the call opened.
     */
    @Test
    void GivenAnOpenedPlan_WhenItsRecordIsRead_ThenItNamesTheOperationAndThePlan() {
        String traceId = TRACE_OF_THE_OPENING;
        MvcTestResult result = mvc.post()
                .uri(PLANS)
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + dateOf(4) + "\"}")
                .exchange();
        String plan = planIdOf(result);

        Map<String, Object> record = owner.sql("""
                        SELECT r.operation, r.outcome, n.kind, n.record_id
                        FROM audit.audit_record r JOIN audit.audit_record_named_record n ON n.audit_record_id = r.id
                        WHERE r.trace_id = ?
                        """).param(traceId).query().singleRow();

        assertThat(record)
                .containsEntry("operation", "openPlan")
                .containsEntry("outcome", "SUCCEEDED")
                .containsEntry("kind", "PLAN")
                .containsEntry("record_id", UUID.fromString(plan));
    }

    // --- requestSolve

    /** #161 step 9: a call from the stand-in Dispatcher reaches the use case behind the interface. */
    @Test
    void GivenAGeneratedOperationWithACapability_WhenADispatcherCallsIt_ThenTheUseCaseRuns() {
        String plan = planIdOf(open(dateOf(10)));

        MvcTestResult result = solve(plan, "\"0\"", "{\"limitSeconds\":30}");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String job = read(result, "$.jobId");
        assertThat(result).headers().hasValue(HttpHeaders.LOCATION, "/api/v1/solve-jobs/" + job);
        assertThat(storedPlan(plan)).containsEntry("status", "SOLVING");
        assertThat(storedJob(job))
                .containsEntry("plan_id", UUID.fromString(plan))
                .containsEntry("phase", "QUEUED")
                .containsEntry("lane", "DAILY")
                .containsEntry("solver", "ortools")
                .containsEntry("solve_limit", 30_000_000_000L);
    }

    @Test
    void GivenNoBody_WhenRequestSolve_Then202AndTheJobHasTheDefaultLimit() {
        String plan = planIdOf(open(dateOf(11)));

        MvcTestResult result = mvc.post()
                .uri(PLANS + "/" + plan + "/solves")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String job = read(result, "$.jobId");
        assertThat(storedJob(job)).containsEntry("solve_limit", 45_000_000_000L);
    }

    /** The contract reads a request's null for an optional field as leaving it out. */
    @Test
    void GivenANullLimit_WhenRequestSolve_Then202AndTheJobHasTheDefaultLimit() {
        String plan = planIdOf(open(dateOf(42)));

        MvcTestResult result = solve(plan, "\"0\"", "{\"limitSeconds\":null}");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(storedJob(read(result, "$.jobId"))).containsEntry("solve_limit", 45_000_000_000L);
    }

    @Test
    void GivenAStaleIfMatch_WhenRequestSolve_Then412() {
        String plan = planIdOf(open(dateOf(12)));

        MvcTestResult result = solve(plan, "\"7\"", "{}");

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("STALE_VERSION");
        assertThat(result).bodyJson().extractingPath("$.params.id").asString().isEqualTo(plan);
        assertThat(storedPlan(plan)).containsEntry("status", "DRAFT");
    }

    @Test
    void GivenNoIfMatch_WhenRequestSolve_Then400MissingParameter() {
        String plan = planIdOf(open(dateOf(13)));

        MvcTestResult result = mvc.post()
                .uri(PLANS + "/" + plan + "/solves")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo("MISSING_PARAMETER");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.name")
                .asString()
                .isEqualTo(HttpHeaders.IF_MATCH);
    }

    /** Each case opens its plan on a date of its own, so no case meets another's plan. */
    static Stream<Arguments> malformedIfMatches() {
        return Stream.of(
                Arguments.of("a wildcard", "*", 30),
                Arguments.of("a weak tag", "W/\"0\"", 31),
                Arguments.of("a list", "\"0\", \"1\"", 32),
                Arguments.of("a bare version", "0", 33),
                Arguments.of("a leading zero", "\"00\"", 34));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedIfMatches")
    void GivenAWildcardOrWeakIfMatch_WhenRequestSolve_Then400WrongFormat(String what, String ifMatch, int day) {
        String plan = planIdOf(open(dateOf(day)));

        MvcTestResult result = solve(plan, ifMatch, "{}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo("WRONG_FORMAT");
        assertThat(storedPlan(plan)).containsEntry("status", "DRAFT");
    }

    @Test
    void GivenTwoIfMatchLines_WhenRequestSolve_Then400WrongFormat() {
        String plan = planIdOf(open(dateOf(15)));

        MvcTestResult result = mvc.post()
                .uri(PLANS + "/" + plan + "/solves")
                .header(HttpHeaders.IF_MATCH, "\"0\"", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo("WRONG_FORMAT");
    }

    @ParameterizedTest
    @CsvSource({"4, TOO_SMALL", "61, TOO_LARGE"})
    void GivenALimitOf4Or61_WhenRequestSolve_Then400TooSmallOrTooLarge(int seconds, String code) {
        String plan = planIdOf(open(dateOf(16 + seconds)));

        MvcTestResult result = solve(plan, "\"0\"", "{\"limitSeconds\":" + seconds + "}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(code);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/limitSeconds");
    }

    @Test
    void GivenASolvingPlan_WhenRequestSolve_Then422PlanNotDraftOrReady() {
        String plan = planIdOf(open(dateOf(90)));
        assertThat(solve(plan, "\"0\"", "{}")).hasStatus(HttpStatus.ACCEPTED);

        MvcTestResult result = solve(plan, "\"1\"", "{}");

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("PLAN_NOT_DRAFT_OR_READY");
        assertThat(result).bodyJson().extractingPath("$.params.plan").asString().isEqualTo(plan);
        assertThat(recordedOutcomes(traceIdOf(result))).containsExactly("REFUSED PLAN_NOT_DRAFT_OR_READY");
        assertThat(jobsOf(plan)).isEqualTo(1);
    }

    /** The lane's sixth job is refused (BR-112), and the refusal changes nothing it would have. */
    @Test
    void GivenFiveQueuedDailyJobs_WhenRequestSolve_Then422SolveQueueFull() {
        String plan = planIdOf(open(dateOf(50)));
        for (int n = 1; n <= 5; n++) {
            queueADailyJob(new UUID(QUEUED_KEYS, n));
        }

        MvcTestResult result = solve(plan, "\"0\"", "{}");

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("SOLVE_QUEUE_FULL");
        assertThat(result).bodyJson().extractingPath("$.params.lane").asString().isEqualTo("DAILY");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.params.limit")
                .asNumber()
                .isEqualTo(5);
        assertThat(recordedOutcomes(traceIdOf(result))).containsExactly("REFUSED SOLVE_QUEUE_FULL");
        assertThat(storedPlan(plan)).containsEntry("status", "DRAFT");
        assertThat(jobsOf(plan)).isZero();
    }

    /** The limit's own bounds pass both the contract's check and the domain's (FR-19). */
    @ParameterizedTest
    @CsvSource({"5, 40", "60, 41"})
    void GivenALimitOf5Or60_WhenRequestSolve_Then202AndTheJobHasThatLimit(int seconds, int day) {
        String plan = planIdOf(open(dateOf(day)));

        MvcTestResult result = solve(plan, "\"0\"", "{\"limitSeconds\":" + seconds + "}");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(storedJob(read(result, "$.jobId"))).containsEntry("solve_limit", seconds * 1_000_000_000L);
    }

    @Test
    void GivenAnUnknownPlan_WhenRequestSolve_Then404NotFound() {
        MvcTestResult result = solve(NO_PLAN, "\"0\"", "{}");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.params.kind").asString().isEqualTo("Plan");
    }

    // --- every answer

    @Test
    void GivenAnyAnswer_WhenAnOperationIsCalled_ThenCacheControlIsNoStoreNoTransform() {
        String plan = planIdOf(open(dateOf(91)));
        List<MvcTestResult> answers = List.of(
                open(dateOf(92)),
                open(dateOf(92)),
                solve(plan, "*", "{}"),
                solve(plan, "\"5\"", "{}"),
                solve(plan, "\"0\"", "{}"),
                mvc.post()
                        .uri(PLANS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + dateOf(93) + "\"}")
                        .exchange());

        assertThat(answers)
                .extracting(answer -> answer.getResponse().getStatus())
                .containsExactly(201, 422, 400, 412, 202, 401);
        assertThat(answers)
                .allSatisfy(answer ->
                        assertThat(answer).headers().hasValue(HttpHeaders.CACHE_CONTROL, NO_STORE_NO_TRANSFORM));
    }

    private MvcTestResult open(LocalDate date) {
        return mvc.post()
                .uri(PLANS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}")
                .exchange();
    }

    private MvcTestResult solve(String plan, String ifMatch, String body) {
        return mvc.post()
                .uri(PLANS + "/" + plan + "/solves")
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String planIdOf(MvcTestResult opened) {
        assertThat(opened).hasStatus(HttpStatus.CREATED);
        return read(opened, "$.planId");
    }

    private static String traceIdOf(MvcTestResult result) {
        return read(result, "$.traceId");
    }

    /** One string of an answer's JSON body. */
    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException unreadable) {
            throw new AssertionError("the answer's body is unreadable", unreadable);
        }
    }

    private Map<String, Object> storedPlan(String plan) {
        return owner.sql("SELECT status, delivery_date::text AS delivery_date FROM planning.plan WHERE id = ?")
                .param(UUID.fromString(plan))
                .query()
                .singleRow();
    }

    private Map<String, Object> storedJob(String job) {
        return owner.sql("SELECT plan_id, phase, lane, solver, solve_limit FROM planning.solve_job WHERE id = ?")
                .param(UUID.fromString(job))
                .query()
                .singleRow();
    }

    private List<String> recordedOutcomes(String traceId) {
        return owner.sql("SELECT outcome || ' ' || coalesce(error_code, '') FROM audit.audit_record WHERE trace_id = ?")
                .param(traceId)
                .query(String.class)
                .list();
    }

    private void endEveryUnfinishedDailyJob() {
        owner.sql("""
                UPDATE planning.solve_job
                SET phase = 'FAILED', failure_reason = 'INTERRUPTED', ended_at = requested_at,
                    claimed_at = NULL, search_started_at = NULL, version = version + 1
                WHERE lane = 'DAILY' AND phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING')
                """).update();
    }

    /** Plants a queued daily job of no plan of these tests', so the lane fills without a solve. */
    private void queueADailyJob(UUID job) {
        owner.sql("""
                        INSERT INTO planning.solve_job
                            (id, version, plan_id, lane, solver, solve_limit, seed, requested_at, phase)
                        VALUES (?, 0, ?, 'DAILY', 'ortools', 45000000000, 7, now(), 'QUEUED')
                        """).params(job, job).update();
    }

    private int jobsOf(String plan) {
        return owner.sql("SELECT count(*) FROM planning.solve_job WHERE plan_id = ?")
                .param(UUID.fromString(plan))
                .query(Integer.class)
                .single();
    }

    private static LocalDate dateOf(int n) {
        return FIRST_DATE.plusDays(n);
    }
}
