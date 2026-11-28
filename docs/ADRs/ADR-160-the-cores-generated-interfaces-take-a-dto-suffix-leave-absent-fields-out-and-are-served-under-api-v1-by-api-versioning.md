# ADR-160: The core's generated interfaces take a `Dto` suffix, leave absent fields out, and are served under `/api/v1` by API versioning

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-140] decision 2 has the core generate its Spring MVC interfaces and models from
`contracts/client-api/v1` at every build, with openapi-generator 7.27.0 and the options it tried on
24 and 25 November 2026, and never commit them. It left #161 to settle the rest:

- what to do with the unused `ApiUtil` class and the error-only media type;
- confirming with #148 that openapi-typescript and `oasdiff` keep the `x-internal` operation;
- with #193, whether the event stream's payloads are generated.

#161 added three more open points:

- **The models' names clash with the domain's.** `Route`, `Stop`, `DropReason`, `PinTarget`,
  `GeoPoint`, `PlanPublished` and others name types in Planning and the kernel. #184's and #214's
  mappers import both kinds side by side.
- **Absent fields.** The contract promises an optional field with no value is left out, never sent
  as `null`. The generated records mark such a field `@Nullable`, and Jackson writes a `null`.
- **The base path.** The contract's `servers` entry is `/api/v1`, and TS §6 serves it "through
  Spring Framework 7's API versioning" ([ADR-043]).

Building #161 with #184's first two operations, on 28 November 2026, showed the following about
the generator:

- **It writes paths without the base path.** Each method's mapping is its path within the
  contract, such as `/plans`, so on its own nothing is served under `/api/v1`.
- **Its supporting files are `ApiUtil` and two `package-info.java` files.** The package-info files
  carry `@NullMarked`, which `ApplicationTest` requires of every adapter package. Turning supporting
  files off drops all three, and `ApplicationTest` then fails.
- **Each class's `@Generated` holds the build's time** unless `hideGenerationTimestamp` is set.
- **`useBeanValidation` puts `@Validated` on each interface,** unless `useSpringBuiltInValidation`
  is set. With it, Spring MVC skips its own validation of the arguments, and Spring Boot's
  method-validation proxy checks them from inside the handler instead. A malformed `If-Match` then
  answered 500 with a `ConstraintViolationException`, after the call wrapper had started, rather
  than the contract's 400.
- **It warns that its OpenAPI 3.1 support is still in beta.**
- **openapi-typescript 7.13.0 keeps an `x-internal` operation.** It was tried on a throwaway copy
  of the contract with an `x-internal` event-stream operation added, and its output types it. The
  Spring generator leaves it out, as ADR-140 found. `oasdiff` isn't set up before January
  ([ADR-094]).

A vetting of the plugin that day found two more facts:
- **7.28.0 came out on 26 November**, after ADR-140's trial. It lists no breaking change.
- **`just audit` doesn't see build plugins.** OSV-Scanner 2.7.0, which it runs, reads neither a
  pom's `<build><plugins>` nor their dependencies: a throwaway pom whose plugin depended on a
  vulnerable `log4j-core` scanned as 0 packages.

## Decision

Decided on 28 November 2026.

1. **The generated models take the suffix `Dto`** (`modelNameSuffix`): `OpenPlanRequestDto`,
   `RequestedSolveDto` and, with #214, `RouteDto`. A mapper reads `RouteDto` beside `Route` without
   an alias, and the suffix says at once which one is the wire's.
2. **`ApiUtil` isn't generated.** `supportingFilesToGenerate` names `package-info.java` alone, so
   only the two `@NullMarked` package-info files are written. `ApiUtil` serves only the default
   methods that `skipDefaultInterface` leaves out. `hideGenerationTimestamp` keeps the build's time
   out, so two builds of one contract write the same classes.
3. **Spring MVC validates the arguments itself** (`useSpringBuiltInValidation`). The interfaces
   carry no `@Validated`, so a request that breaks the contract answers 400 through
   `ContractErrorHandler` before the handler starts, in the order [ADR-140] decision 9 sets.
4. **The error-only media type stays as generated.**
   - An operation whose only declared body is the error body declares only
     `application/problem+json`, so a client whose `Accept` names only `application/json` would
     get 406.
   - Both apps' client sends `Accept: application/json, application/problem+json` on every call,
     which matches every generated `produces`. #148 builds it.
   - The first operation with no success body adds an integration test that calls it with that
     `Accept`.
   - Both of #184's operations answer a JSON body, so each declares both types.
5. **The served JSON converter leaves an absent value out** (`NON_ABSENT`), in `web.ServedJson`,
   beside its strict reading ([ADR-140] decision 9).
   - It covers every answer the core serves in one place: the generated models, the problem body
     and the error endpoint's.
   - The shared mapper, and so the optimizer client, keeps its own settings.
6. **The generated operations are served under `/api/v1` through Spring Framework 7's API
   versioning,** as TS §6 decides.
   - `web.ClientApiPaths` adds the prefix `/api/{version}` to every class that implements a
     generated interface.
   - It reads the version from the path's second segment, only for a path under `/api/`, with `1`
     the one supported version and no version required elsewhere. So the error endpoint, the health
     probes and the `e2e` endpoints keep their paths.
   - `shared.adapter.in.web.ClientApi` names `/api/v1` once, for the controllers' `Location`
     headers and the access interceptor's pattern.
7. **The generator stays at 7.27.0, the version ADR-140 tried.** 7.28.0 comes as a Dependabot
   update, and it is taken once its build passes and its generated interfaces are read for changes.
8. **The x-internal check is done for openapi-typescript and waits for `oasdiff`.** The first pull
   request that runs `oasdiff` checks that it keeps the stream's operation. #193 settles whether
   the stream's event payloads are generated models; if they are, rule 22 admits Planning's and Live
   Operations' `sse` adapters.

## Consequences

- **Easy:**
  - A controller imports the domain's types and the wire's side by side.
  - An absent field can't slip into an answer by a model or a handler that forgets it.
  - A `v2` served beside `v1` is one more supported version and one more generated package.
- **Hard:**
  - The generated `ProblemDto` goes unused: the core writes its problems through `Problems` and
    Spring's `ProblemDetail`. It is generated, not maintained, so it costs nothing to keep.
  - Each controller method takes the two client headers the contract declares, `X-App` and
    `X-App-Version`, and ignores them: the access interceptor reads them into the audit record
    ([ADR-082]).
  - Spring's API versioning refuses a version it doesn't serve, such as `POST /api/v2/plans`, with
    400. `ContractErrorHandler` answers it 404 `NO_SUCH_OPERATION` instead, as any path no operation
    has, so one more Spring exception is mapped by hand. Such a path still meets Spring's other
    checks first, as a `v1` path does: a body that isn't JSON answers 415, and a method no
    operation of the path has answers 405.
- **Cost:** none in tools. The plugin is [ADR-043]'s.
- **Risk:**
  - The generator's OpenAPI 3.1 support is in beta. A 3.1 keyword it doesn't map, such as
    `Problem`'s `if` and `then`, leaves no check in the generated model. That check matters only if
    the core ever reads a `ProblemDto`, which it doesn't.
  - `just audit` doesn't scan the plugin or its dependencies. They run at build time only, on the
    repository's own contract. The exact pin and Dependabot keep the plugin current until a scanner
    reads plugins.

## Alternatives considered

- **`@JsonInclude` on each model** (`generateJsonIncludeAnnotations`). Rejected: it covers the
  generated models only, not the problem body or a handler's own answer.
- **Supporting files off, with `@NullMarked` package-info files written by hand.** Rejected: hand
  files in a generated package go stale, and `.openapi-generator-ignore` for `ApiUtil` alone would
  need one more file for the same result.
- **The base path in each generated interface,** as a `@RequestMapping` with a property default.
  Rejected: TS §6 decided API versioning, and a property would let a deployment move the API off
  the path its contract names.
- **No suffix, the models told apart by their package.** Rejected: every mapper would alias one of
  each pair. `Json` and `Body` were weighed too. `Body` reads oddly for a route's stops inside an
  answer, and `Dto` is openapi-generator's own example of the option.

## Revisit when

- The contract gains a `v2`.
- The generator's 3.1 support leaves beta, or a contract keyword the core relies on is dropped
  from the generated code.
- OSV-Scanner, or another scanner `just audit` runs, reads build plugins.
- #193 builds the event stream.

[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-082]: ADR-082-supported-phone-app-versions.md
[ADR-094]: ADR-094-each-pull-requests-contract-check-compares-against-its-base-branch.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
