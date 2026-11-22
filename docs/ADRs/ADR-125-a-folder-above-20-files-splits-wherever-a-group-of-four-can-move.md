# ADR-125: A source folder above 20 files splits wherever a group of four files can move

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

[ADR-118] splits a Java package of more than 30 files: a domain by aggregate, and an application
package by aggregate too. Its threshold binds every source folder. It keeps the shared kernel, each
`events` package and `orders/domain` flat. On 2026-11-22 only one other folder was over 30, the
ArchUnit `io` fixtures at 47 files, so a folder could still open onto 29 loose files.

On 2026-11-22 the owner, Naim Elijah, asked that no folder open onto a long list of loose files,
wherever its files can be grouped into subfolders.

What the tree held on 2026-11-22, on the branch that renamed the components ([ADR-123]). The counts
leave out `package-info.java`:

- 41 folders held more than 10 files: 26 production packages in `core`, 10 test packages in `core`,
  and 5 other folders. 19 held more than 20 files, among them `docs/ADRs` with 124 ADRs and its
  index.
- Three read-only analyses checked every one of them:
  - jdeps and javap on compiled classes, which were newer than their sources, confirmed against the
    sources;
  - the optimizer's import-linter contracts, the tools' configuration, and the links between files.

They found:

- **The application packages bind nothing.** None of the 15 application packages of 15 files or more
  has a package-private member or a non-public type, and none has a cycle. Only the rule that a root
  imports no subpackage holds their files together.
- **Some domain packages bind a few files and leave groups free.** In `liveops/domain/delivery`,
  `Delivery` calls package-private members of `DeliveryInvariants` and `DeliverySnapshot`, but the
  outcome and proof values use nothing in the package. In `reporting/domain`, the package-private
  `OnTimeCounts` ties the on-time rates to the root, and `Baseline` uses its own 10 values.
- **`orders/domain` binds all 41 files.**
  - The cycle `OrdersError` → `OrderStatus` → `Order` → `OrdersError` holds 11 files.
  - `ImportBatch` calls package-private members of `Order`, `Customer`, `OrderDetails`,
    `ImportContext` and `DateCapacity`.
  - The root imports no subpackage, so everything those types reach stays too.
- **`planning/domain/plan` (30)** could shed only groups of 3 and 2 files.
- **A module's published packages are its contract.** [DL §2.3][DL-23], rule 4, makes a module's
  contract three packages, `api`, `spi` and `events`, each a `@NamedInterface`. `ApplicationTest`
  expects `Accounts` and `PublishedRoutes` in their module's `api` itself, and asserts that every
  `spi` interface sits in its `spi` package.
- **The kernel could shed its 14 typed IDs** into one package, but 367 production and 48 test files
  import them. `DomainPurityRules` allows a Spring Modulith annotation only on the kernel root's
  `package-info`, and M0 decides how Spring Modulith exposes `shared` (ADR-118, Revisit when).
- **`docs/ADRs` would cost about 2,500 link edits.** Moving the ADRs into the index's topic folders
  breaks:
  - 149 of the 491 links between ADRs;
  - all 1,769 links from the ADRs to other files;
  - the index's 124 links;
  - 482 links in 27 other files;
  - every link to an ADR in GitHub issues and pull requests.

  `docs/CLAUDE.md` forbids rewriting an accepted ADR.
- **ApplicationRules rule 7 checks only two roots.** Its root check covers `application.service` and
  `application.port.out`, not a deeper package that gains subpackages.

## Decision

This amends rules 1, 5 and 7 of [ADR-118]. The rest of ADR-118 stands.

1. **The threshold.** A source folder of more than 20 files splits wherever a group of at least 4 of
   its files can move under ADR-118 and this ADR. A source folder is a Java or Python package, a
   TypeScript folder, or a test folder. Java counts leave out `package-info.java`, and Python counts
   leave out `__init__.py`.
   - A folder of 20 or fewer stays flat.
   - A group of fewer than 4 files stays where it is.
   - What is left in the folder may hold fewer than 4 files.
   - A package that is already split stays split.
2. **Concept packages.** ADR-118 rule 5 applies to any domain package over 20 files, not only over
   30.
   - A concept package may hold two [DL §17][DL-17] concepts when one is the fact of the other.
     `planning.domain.drop` holds Drop and Excluded vehicle, because an excluded vehicle is the fact
     behind a *no vehicle available* drop.
   - One concept package may use another, one way, with no cycle: `liveops.domain.outcome` uses
     `liveops.domain.proof`, because a delivered outcome carries its proof. The rest of ADR-118
     rule 7 stands.
3. **Inside an aggregate's application package.** The package may split by a concept beneath it, as
   `<module>.application.<layer>.<aggregate>.<concept>`. It takes its name by ADR-118 rule 6, for
   example `plan.edit` (from `Edit`, which has no §17 row) and `account.credentiallink`.
   - A package imports none of its own subpackages.
   - ApplicationRules rule 7 checks every application package that has subpackages, not only the
     `service` and `port.out` roots.
4. **What stays flat whatever its size:**
   - a module's `api`, `spi` and `events` packages, which are its contract ([ADR-040], DL §2.3
     rule 4);
   - the shared kernel's domain types, and their tests, until M0 decides how Spring Modulith exposes
     `shared` (ADR-118 rule 9, the owner's choice);
   - files a tool reads from a fixed place: `package.json`, the `tsconfig` files, `pom.xml`, the
     Maven wrapper, `Dockerfile` and `.dockerignore`, and the dotfiles at the root;
   - `docs/ADRs`, which is not a source folder. It stays one folder, numbered, with
     `docs/ADRs/README.md` grouping the ADRs by topic (the owner's choice).

Applied now, this splits:

| Folder | Files | Groups |
|---|---|---|
| `orders.application.service` | 27 | `customer` 8, `order` 12, `importbatch` 5; 2 stay |
| `fleet.application.service` | 25 | `driver` 7, `register` 8, `roster` 8; 2 stay |
| `planning.application.service.plan` | 29 | `edit` 7, `route` 6; 16 stay |
| `planning.application.port.out` | 27 | 6 to its `plan`, 1 to its `settings`; 20 stay |
| `liveops.application.port.out` | 24 | `routerun` 6; 18 stay |
| `access.application.service.account` | 21 | `credentiallink` 6; 15 stay |
| `access.application.port.out` | 21 | `session` 4; 17 stay |
| `orders.application.port.out` | 25 | `customer` 4; 21 stay |
| `planning.domain.solution` | 25 | `planning.domain.drop` 4, and `PinTarget` to `planning.domain`; 20 stay |
| `liveops.domain.delivery` | 24 | `liveops.domain.outcome` 4, `liveops.domain.proof` 4; 16 stay |
| `reporting.domain` | 21 | `baseline` 11; 10 stay |
| The `io` ArchUnit fixtures | 47 | by the kind of API each exercises: `locale` 14, `thread` 13, `classpath` 8, `process` 8; 4 stay |
| The `error` ArchUnit fixtures | 25 | `text` 8, `personalvalue` 10; 7 stay |
| `src/frontend/mobile` | 13 | `index.ts`, `App.tsx` and `App.test.tsx` into `src/`, as in the web app; 10 configuration files stay |

The tests of a moved type move with it (ADR-118 rule 10).

These stay flat:
- **Bound:** `orders.domain` (its closure is all 41 files) and `planning.domain.plan` (groups of 3
  and 2).
- **Published:** the `api` packages of Planning (17) and Access (15), and the `events` fixtures
  (25), which stand in for an `events` package.
- **The kernel (38)**, and its tests (21).
- **Groups under 4:** Orders' ports for `order` (3) and `importbatch` (2), Access's ports for
  `account` (3), and Solution's withdrawn-vehicle values (2).
- **Folders of 20 or fewer:** every other folder, the optimizer's `domain` (16) among them.

## Consequences

- **Easy:** no source folder over 20 files holds a group of 4 that could move. An aggregate's use
  cases and ports sit under its own name, as Planning's, Access's and Live Operations' already did.
- **Easy:** a new file has one place: with its aggregate, under its concept, or in the root when
  several use it.
- **Hard:** the tree is deeper, and a type moves when its users change, as under ADR-118.
- **Hard:** a group of 3 next to a folder of 21 stays, so a few folders remain just over 20.
- **Cost:** about 110 production files, 61 fixtures and 3 frontend files move, with the tests of the
  moved types. One ArchUnit rule is widened. The documents that name the moved paths change with
  them.
- **Risk:** a branch open during the move conflicts on imports, and a file it adds lands in the old
  package. Each module moves in its own commit, with nothing in it but the move and the paths that
  name it.

## Alternatives considered

- **Keep ADR-118's 30.** Rejected: folders of 21 to 30 loose files, against the owner's request.
- **A threshold of 15.** Rejected by the owner. It adds groups of 3 to 4 files to folders that
  already read well, such as Access's `role` values and Messaging's services, and splits the
  optimizer's 16-file `domain`.
- **Every aggregate gets its subpackage, however small,** as Planning's one-file
  `port.out.proposal` does. Rejected: it makes folders of 2 and 3 files.
- **Split the `api` packages.** Rejected: DL §2.3 makes a module's contract three packages, and
  whether one named interface covers its subpackages is unsettled until M0.
- **Split the kernel now**, its 14 typed IDs into `shared.id`. Rejected by the owner for now: it
  changes the imports of 415 files, and M0 decides how Spring Modulith exposes `shared`.
- **Move the ADRs into topic folders.** Rejected by the owner: about 2,500 link edits inside
  accepted ADRs, and every outside link to an ADR breaks.
- **Break the Customers & Orders cycle now.** Rejected: [DL §9.6][DL-96] names
  `OrderStatus.derive(order, placement, progress)`, and without it 36 files would still be bound by
  package-private calls.

## Revisit when

- M0 decides how Spring Modulith exposes `shared`, or whether a named interface covers its
  subpackages. Then reconsider the kernel's IDs and the `api` packages.
- DL §9.6 drops the `OrderStatus.derive(Order, …)` overload. ADR-118's trigger for Customers & Orders
  stands.
- A folder this ADR leaves over 20, such as `planning.domain.plan`, passes 30 with no group of 4 free.
- A published package passes 20 files.
- The team finds 20 wrong in practice.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-118]: ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[ADR-123]: ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md
[DL-17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-96]: ../architecture/domain_layer/domain_layer_design.md#96-order-status-br-33-is-derived-not-stored
