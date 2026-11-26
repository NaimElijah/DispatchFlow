# ADR-142: The call wrapper's retry, its 400s and its error endpoint, as #162 built them

- **Status:** Accepted; amended by ADR-149
- **Date:** 2026-11-25

## Context

[ADR-140] decisions 5, 6 and 9 say what [#162][ISSUE-162] builds: the status of each error, the call
wrapper that retries, and the strict JSON converter with its size limit. Building them, and a review
of the build on 25 November 2026, showed that some of their words don't hold as written and that four
points were left open. What they say:

- **Decision 6:** "A try is retried only while none of its transactions has committed", and the
  wrapper "rethrows instead". The rethrown exception is the race, which decision 5's table answers
  with 503 and `Retry-After`.
- **Decision 6:** "a retried handler takes no streaming parameter, such as an `InputStream`". Nothing
  enforces it.
- **Decision 9:** a failed validation "lists every field", and "every other body has a named size
  limit, which answers 413".
- **Decision 5:** "Every answer is the `Problem` of [ADR-043]", and `UnexpectedErrorHandler` "runs
  last". It says nothing of the order of the other handlers.
- **Decision 10:** the async executor has "a named concurrency limit". It says nothing of the queue
  behind that limit.

What the build and the review found:

- **A split use case commits a read first.** Its `Change` reads in `readOnly` transactions before it
  saves ([ADR-129] decision 3). Read literally, decision 6 counts each of those commits, so a split
  use case would never be retried.
- **A race after a stored change answers 503.** When a read-write transaction of a try has
  committed and a later step of the same call loses a race, the wrapper rethrows the race, and
  decision 5's table answers 503 `TRY_AGAIN_LATER` with `Retry-After`. A client that obeys the header
  sends the call again, and the change, already stored, is made twice.
- **Spring's choice of advice.** In Spring Framework 7.0.10, `ExceptionHandlerExceptionResolver`
  asks each `@ControllerAdvice` in order. Each advice tries the thrown type and then each of its
  causes, before the next advice is asked. When the handler it picked throws, no later advice runs,
  and `ResponseEntityExceptionHandler.handleException` rethrows a top-level type it doesn't know. So
  the order of the advices decides the answer for a wrapped exception.
- **Some errors reach no advice.** An exception that escapes every advice, and one thrown in a
  servlet filter, reach Spring Boot's `BasicErrorController`. Its JSON has no `code` and no
  `traceId`.
- **A 400 had no bound.** It listed every failing item. A crafted 1 MiB list body gave about a
  million failures, and validating it used 311 MB, the review's measurement.
- **Two reads go around the body cap.** The cap covers only a read through a converter. A handler
  that takes an `InputStream`, a `Reader`, a servlet request or response, an `OutputStream` or a
  `Writer` reads the body itself. Spring's `FormContentFilter` is on by default
  (`spring.mvc.formcontent.filter.enabled`) and reads a PUT, PATCH or DELETE form body whole before
  the request is routed.
- **The executor's queue is unbounded.** [`SchedulingConfiguration`][SCHED] caps the async threads
  at 4, and `spring.task.execution.pool.queue-capacity` is unset: Spring Boot's metadata says an
  unbounded capacity ignores `max-size`. No measurement shows the queue growing.

## Decision

Decided on 25 November 2026. This amends [ADR-140] decisions 5, 6 and 9 and adds to decision 10;
everything else in it stands.

1. **A read-only commit doesn't block a retry.** A try counts as committed only when a read-write
   transaction of it committed, or tried to and failed, because the outcome is then unknown. A
   read-only commit changes nothing, and a split use case's `Change` reads in read-only transactions
   before it saves ([ADR-129]).
2. **A race after a read-write commit in the same try answers 500 `UNEXPECTED_ERROR`.**
   - The wrapper doesn't retry it, doesn't write a refused audit record, and logs it at ERROR.
   - The change is stored, so a 503 would invite a duplicate, and a "refused" record would say the
     opposite of what happened.
   - It holds for every race, a 412 or a 409 included: after a reload, a client would send the
     stored change again. It replaces decision 5's 503, 412 and 409 for the one case where a
     read-write transaction of the try has committed.
3. **A 400 lists at most 100 of its failing items, and a 422 lists every refusal.**
   - The limit is the constant `MAX_ITEMS = 100`. It cuts the body and the 400's log line alike, and
     the log line carries the total count under a key of its own.
   - The items keep the order validation found them in, which needn't be the body's order.
   - The contract gives `errors` `maxItems: 100` for a 400 and says it lists at most 100.
   - The cap bounds the answer and its log line, not the work before them: validation still
     builds every failure. The request's own bound is the contract's `maxItems` on each request
     array, which the generated models enforce (#161).
   - A 422's `errors` come from `RuleViolations`, which the domain bounds, such as an import's rows,
     so they stay uncapped.
4. **The handlers run in this order: a module's, the contract's, the kernel's, then
   `UnexpectedErrorHandler`.** `HandlerOrder` holds the four `@Order` values.
   - *Module first.* A module's own exception that wraps a Spring MVC exception, such as an
     optimizer outage wrapping a `RestClientException(HttpMessageNotReadableException)`, reaches its
     module's handler. With the contract's handler first, that handler would claim it by its cause,
     rethrow it as a type it doesn't know, and no later handler would run.
   - *Contract before kernel.* A converter that wraps a `RuleViolation` in a
     `MethodArgumentTypeMismatchException` answers 400, because the contract's handler matches the
     thrown type. With the kernel first, its handler would match the cause and answer 422.
   - *Unexpected last.* It answers any exception the three don't map.
   - A module's handler therefore maps only its own exception types. It builds its body with
     `shared.adapter.in.web.Problems` and takes `@Order(HandlerOrder.MODULE)`.
5. **A last-resort error endpoint answers every error Tomcat dispatches as a `Problem`.**
   - It is a package-private `@RestController` in `web` that implements Spring Boot's
     `ErrorController`, so `BasicErrorController` backs off. This covers an exception that escapes
     every advice and one thrown in a servlet filter.
   - It answers the contract's `Problem` as `application/problem+json`, a 400 with one item for the
     whole request, and `instance` set to the path that failed. The status is the
     dispatch's, and the code comes from the same status table the contract's handler uses, in
     `WebCodes`; a status the table lacks answers 500. A request for the error path itself carries
     no status and answers 404 `NO_SUCH_OPERATION`, so no client can write ERROR lines at will.
   - It logs a 5xx at ERROR with the exception, and a 4xx at INFO with the status only.
   - `TraceIdFilter` already runs on an error dispatch and keeps the first pass's trace ID, so the
     body carries it.
6. **No handler takes a streaming parameter, and no form body is read before the cap.**
   - `ApplicationRules` rule 16 fails the build when a request handler declares a parameter of type
     `InputStream`, `Reader`, `ServletRequest`, `ServletResponse`, `OutputStream` or `Writer`, or of
     a subtype. A request handler is a method of a class annotated `@Controller`, directly or
     through `@RestController`, or a method annotated `@RequestMapping` or one of its variants, so
     the generated `<Tag>Api` interfaces are covered. Filters, exception handlers and the wrapper
     itself take the servlet request, and the adapter never re-invokes them. An
     `InputStreamResource` is refused too, because Spring's resource converter hands it over as a
     live stream. The rule reads raw parameter types, so a stream inside a type argument, as in
     `HttpEntity<InputStream>`, is left to review.
   - The retry re-invokes a handler with the arguments it already read, which ADR-140 decision 6
     already says; the rule makes that hold for every parameter type it reads.
   - `spring.mvc.formcontent.filter.enabled` is `false`, because no operation takes a form body, and
     `server.tomcat.max-http-form-post-size` is 1 MB, the cap's size.
7. **The async queue stays unbounded until a load test shows it growing.** The trigger is the load
   test of [NFR-10], measured against [NFR-239]'s definition of an error. Then the queue is bounded,
   and the overflow is decided: reject, run on the caller's thread, or block it.
   `SchedulingConfiguration`'s Javadoc names this trigger.

## Consequences

- **Easy:**
  - A split use case is retried, and its read commits no longer end its tries.
  - A module's handler answers its own wrapped exception without the contract's handler taking it.
  - Every error answer that reaches the application is a `Problem` with a `code` and a `traceId`,
    one raised by a filter included. A connector-level refusal, such as a malformed request line,
    never reaches it.
  - A 400's body and log line have a fixed size, however long the request's list is. The
    validation before them doesn't, until #161's `maxItems` bounds each request array.
  - "No streaming parameter" is a build failure, not a review point.
- **Hard:**
  - A caller whose change was stored gets a 500 and can't tell it from a call that failed. It
    reloads, and it reports the `traceId`. The log line holds the detail.
  - A client that fixes a 400 sees the failing items beyond the first 100 only after it resubmits.
  - A module's handler that maps a broad type, such as `RuntimeException`, would take the contract's
    errors, so each maps only its own exceptions.
  - A new operation can't stream a body in or out through a handler parameter. One that must needs
    this ADR reopened.
  - An operation that needs a form body turns the filter on again, and sets its own cap.
- **Cost:** no new dependency. One class in `web`, `ErrorEndpoint`; `TraceIds` in
  `shared.adapter.in.web`; `ApplicationRules` rule 16 with its fixtures; three properties.
- **Risk:**
  - MockMvc doesn't dispatch to the error path, so the error endpoint's tests set the request
    attributes Tomcat would set. No test sends a request through Tomcat to it.
  - The order of the advices rests on Spring's behaviour as read from 7.0.10. A test for each
    case in decision 4 fails when an upgrade changes it; the next is Spring Boot 4.2, in the
    January break ([TS §20][TS-20]).
  - A client that retries every 5xx, as the phone's outbox does ([ADR-140] decision 5, [ADR-087]),
    sends the call again after a 500 too. The difference from a 503 is that no `Retry-After` asks
    for it.
  - The queue grows without a bound under a burst of events, until the load test or an incident
    shows it.
  - Until [#180][ISSUE-180] makes `AuditLog` a bean, the wrapper's lookup of it fails when a race is
    to be audited (`CallRetry`'s Javadoc), so a race it audits answers 500. Decision 2's race never
    reaches that lookup.

## Alternatives considered

- **Any commit blocks the retry, as decision 6 reads.** Rejected: every split use case commits its
  read first, so none would be retried.
- **Answer 503 after a stored change, as decision 5's table gives.** Rejected: `Retry-After` asks
  the client to send the call again, and the change is made twice.
- **Retry after a commit.** Rejected: the same duplicate, made by the wrapper itself.
- **List every failing item of a 400.** Rejected: one crafted list body gave about a million
  items, and the answer and its log line would carry each one.
- **Answer a 412 or a 409 after a commit, as decision 5's table gives.** Rejected: a client that
  reloads and sends the call again makes the stored change twice, as with a 503.
- **Cap a 422 too.** Rejected: each entry is a refusal the user must read, and the domain already
  bounds the list.
- **Contract, module, kernel, unexpected: the order the build first used.** Rejected: a module's
  wrapped exception would reach the contract's handler through its cause.
- **Leave Spring Boot's `BasicErrorController` to answer the rest.** Rejected: its JSON has no
  `code` and no `traceId`, so decision 5's "every answer is a `Problem`" would be false.
- **Allow a streaming parameter and skip the retry for that handler.** Rejected: whether a call
  is retried would hang on a signature, and the body cap would still not cover the read.
- **Bound the async queue now.** Rejected: nothing shows it growing, and a bound needs an overflow
  answer that nothing yet shows the load needs.

## Revisit when

- The load test ([NFR-10]) shows the async queue growing: bound it, and decide the overflow.
- `spring.threads.virtual.enabled` is turned on: the cap on the async executor is decided again, with
  its overflow, because `SimpleAsyncTaskExecutor`'s limit blocks the caller.
- A client must tell a 500 after a stored change from other 500s: it gets a code of its own, in a
  new ADR.
- A real request has more than 100 independently failing items that a client must see at once.
- An operation must stream a body, or take a form body: rule 16 and the filter setting are
  reconsidered for that operation.
- A Spring upgrade changes how advices are resolved, or how Boot's error dispatch reaches
  `ErrorController`: decisions 4 and 5 are checked against the new version.
- [#180][ISSUE-180] decides that a call that lost a race after a stored change is audited.

[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-129]: ADR-129-transactions-start-only-in-the-service-layer.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[SCHED]: ../../src/backend/core/src/main/java/com/dispatchflow/SchedulingConfiguration.java
[ISSUE-162]: https://github.com/NaimElijah/DispatchFlow/issues/162
[ISSUE-180]: https://github.com/NaimElijah/DispatchFlow/issues/180
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-239]: ../requirements/requirements.md#nfr-239
[TS-20]: ../architecture/tech_stack.md#20-adoption-order
