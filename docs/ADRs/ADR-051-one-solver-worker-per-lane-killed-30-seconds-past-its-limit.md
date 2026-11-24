# ADR-051: Each lane has one solver worker process, killed 30 seconds past its limit

- **Status:** Accepted; extended by ADR-131
- **Date:** 2026-11-13

## Context

A solve can run for 60 seconds of pure CPU. A re-optimization has a 20-second limit ([BR-84]). The
optimization service also has to answer `/evaluate`, `/baseline` and its health checks in
milliseconds ([NFR-08]).

The requirements say no plan stays `solving`: a solve that has started and has not returned within 30
seconds past its limit fails, and the failed-solve transition returns the plan to `draft` or `ready`
([BR-32], [BR-59]). The limit runs from the start of the search, after the travel-time matrix is
built ([BR-75]). A queued job waits at most 5 minutes ([BR-112]).

On 2026-11-13 the owner, Naim Elijah, decided the process model for the team. This ADR settles the
marker "ADR pending: solve job timeout". It follows [PL §7.6] and [TS §7.2].

## Decision

- **One HTTP process.** Uvicorn answers `/evaluate`, `/baseline` and the health checks itself.
  `/evaluate` and `/baseline` are plain `def` endpoints, which FastAPI runs in its thread pool, so
  their CPU work never blocks the event loop that waits on the solver workers.
- **One solver worker process per lane,** the daily lane and the re-optimization lane, started with
  `multiprocessing` in `spawn` mode. A solve never runs in the process that answers HTTP.
- **A worker is ready when it has loaded everything.** It imports OR-Tools and compiles the Numba
  kernels when it starts, with `cache=True`, and `/ready` waits for both workers ([TS §7.2]).
- **One job per lane at a time.** The HTTP handler passes the problem to its lane's worker over a
  pipe and awaits the answer without blocking the event loop. The core never sends a second job to a
  busy lane, because its own queue is per lane ([BR-112]).
- **The overrun backstop.** If a worker has not answered 30 seconds past its limit, the HTTP process
  kills it, starts a new one and answers 504 `SOLVE_TIMEOUT` ([BR-32], [BR-84]).
- **Validation runs inside the limit** ([NFR-119]). In the study, the search budget is the limit
  minus the validator's measured 99th-percentile time for that instance size, so receiving to
  returning, validation included, stays inside the 5 seconds of [NFR-25].
- **The core backs this up twice** ([PL §13.1], [PL §11.4]):
  - its client timeout for the optimizer call is the limit plus 30 seconds;
  - a queue watchdog runs every 15 seconds and fails any job queued more than 5 minutes or running
    more than 30 seconds past its limit.
- **The 30-second grace and the client timeout come from configuration,** and tests set them to
  milliseconds, so no test waits 30 seconds. Two tests cover the backstops: an optimizer integration
  test with a fake worker that hangs, which checks that the HTTP process kills it, starts a new one
  and answers 504 `SOLVE_TIMEOUT`; and a core adapter test with a WireMock delay, which checks that
  the client times out and fails the job.
- **All three timers end the job through the same method.** The first to fire fails the job. The
  others find it ended and stop ([DL §11]). A restart fails running jobs, keeps queued daily solves
  and fails queued re-optimizations ([BR-74], [BR-85], [NFR-12]).

## Consequences

- **Easy:** no plan stays `solving`, whether the solver hangs, the optimizer dies or the core
  restarts. Each layer is a backstop for the one before.
- **Easy:** health checks and edit checks stay fast while a solve runs, because CPU-bound search is
  in another process.
- **Easy:** the Numba kernels compile once per worker, not once per request.
- **Hard:** a killed worker takes its start-up cost again: OR-Tools imports and kernel compilation.
  `/ready` stays down until both workers are back. The sources do not measure that time.
- **Hard:** one job per lane at a time. A second job waits in the core's queue, at most 5 minutes,
  with at most 5 queued ([BR-112]).
- **Cost:** process management and a pipe protocol in the optimizer, and a `SOLVE_TIMEOUT` error
  code that both sides handle.
- **Risk:** a re-optimization that hangs holds its lane for 20 + 30 = 50 seconds before it fails,
  which is longer than [NFR-07]'s 30 seconds from request to preview.
- **Risk:** the 30 seconds is a chosen slack. The sources give no measurement behind it, and none of
  this has been run.
- **Risk:** the sources do not say why `spawn` was chosen over `fork`.

## Alternatives considered

- **Solve in the HTTP process.** Rejected: a solve is up to 60 seconds of CPU, and the process also
  answers `/evaluate` in milliseconds.
- **One worker for both lanes.** Not chosen: a re-optimization would wait for a daily solve.
- **Rely on the solver's own time limit.** Not chosen: [BR-32] says no plan stays `solving`, and a
  solve that never returns would leave one.

## Revisit when

- A worker is killed in the pilot or in the load test. Measure its restart time, and reconsider the
  30 seconds of slack.
- The load test misses [NFR-07]'s 95th percentile because jobs wait for a busy lane. Then more than
  one worker per lane is worth its cost.
- A solver needs a different runtime or must scale on its own, as [ADR-004] says.

[ADR-004]: ADR-004-pluggable-solver-port.md
[BR-32]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-59]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-74]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-75]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-84]: ../requirements/requirements.md#re-optimization
[BR-85]: ../requirements/requirements.md#re-optimization
[BR-112]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[DL §11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[NFR-07]: ../requirements/requirements.md#nfr-07
[NFR-08]: ../requirements/requirements.md#nfr-08
[NFR-12]: ../requirements/requirements.md#nfr-12
[NFR-25]: ../requirements/requirements.md#nfr-25
[NFR-119]: ../requirements/requirements.md#nfr-119
[PL §7.6]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#76-process-model
[PL §11.4]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#114-scheduled-jobs-and-watchers
[PL §13.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#131-solve
[TS §7.2]: ../architecture/tech_stack.md#72-process-model
