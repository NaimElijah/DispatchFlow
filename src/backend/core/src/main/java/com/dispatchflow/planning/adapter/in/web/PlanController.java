package com.dispatchflow.planning.adapter.in.web;

import com.dispatchflow.access.application.api.CurrentActor;
import com.dispatchflow.planning.application.service.plan.OpenPlan;
import com.dispatchflow.planning.application.service.plan.OpenedPlan;
import com.dispatchflow.planning.application.service.plan.RequestSolve;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.adapter.in.web.ClientApi;
import com.dispatchflow.shared.adapter.in.web.api.PlanningPlansApi;
import com.dispatchflow.shared.adapter.in.web.model.OpenPlanRequestDto;
import com.dispatchflow.shared.adapter.in.web.model.OpenedPlanDto;
import com.dispatchflow.shared.adapter.in.web.model.RequestSolveRequestDto;
import com.dispatchflow.shared.adapter.in.web.model.RequestedSolveDto;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Serves a delivery date's plans: opens one and requests its solve (FR-15, FR-16, FR-19, BR-34,
 * BR-76, BR-118). It maps the contract's types to the use cases' and back, and decides nothing.
 *
 * <p>A plan's version travels as a strong entity tag, {@code "<version>"}: the 201 sends it, and a
 * change sends it back as {@code If-Match} (ADR-057, ADR-157). The generated interface's pattern has
 * already refused any {@code If-Match} but one such tag, so this reads it without checking it again.
 *
 * <p>Both operations declare the client headers {@code X-App} and {@code X-App-Version}, which the
 * access interceptor has already read into the call's context for its audit record (ADR-082), so
 * the parameters go unused here.
 */
@RestController
class PlanController implements PlanningPlansApi {

    private static final String PLAN_PATH = ClientApi.BASE_PATH + "/plans/{planId}";
    private static final String SOLVE_JOB_PATH = ClientApi.BASE_PATH + "/solve-jobs/{jobId}";
    private static final String QUOTE = "\"";

    private final OpenPlan openPlan;
    private final RequestSolve requestSolve;

    PlanController(OpenPlan openPlan, RequestSolve requestSolve) {
        this.openPlan = openPlan;
        this.requestSolve = requestSolve;
    }

    @Override
    public ResponseEntity<OpenedPlanDto> openPlan(
            OpenPlanRequestDto openPlanRequestDto, @Nullable String xApp, @Nullable String xAppVersion) {
        OpenedPlan opened = openPlan.open(CurrentActor.get(), openPlanRequestDto.date());
        UUID plan = opened.plan().value();
        return ResponseEntity.created(path(PLAN_PATH, plan))
                .eTag(entityTag(opened.version()))
                .body(new OpenedPlanDto(plan));
    }

    @Override
    public ResponseEntity<RequestedSolveDto> requestSolve(
            UUID planId,
            String ifMatch,
            @Nullable String xApp,
            @Nullable String xAppVersion,
            @Nullable RequestSolveRequestDto requestSolveRequestDto) {
        Optional<SolveLimit> limit = Optional.ofNullable(requestSolveRequestDto)
                .map(RequestSolveRequestDto::limitSeconds)
                .map(seconds -> SolveLimit.daily(Duration.ofSeconds(seconds)));
        JobId job = requestSolve.request(
                CurrentActor.get(), PlanId.of(planId), Optional.empty(), limit, versionOf(ifMatch));
        return ResponseEntity.accepted()
                .location(path(SOLVE_JOB_PATH, job.value()))
                .body(new RequestedSolveDto(job.value()));
    }

    /** A record's path under the client API, as a relative reference, never with a host. */
    private static URI path(String template, UUID id) {
        return UriComponentsBuilder.fromPath(template).buildAndExpand(id).toUri();
    }

    private static String entityTag(Version version) {
        return QUOTE + version.value() + QUOTE;
    }

    /** The version an {@code If-Match} names, its form already checked by the contract's pattern. */
    private static Version versionOf(String ifMatch) {
        return Version.of(Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1)));
    }
}
