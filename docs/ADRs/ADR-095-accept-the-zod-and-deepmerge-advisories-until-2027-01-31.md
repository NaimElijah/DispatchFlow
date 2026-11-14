# ADR-095: Accept the zod and deepmerge advisories in the phone app's tooling until 2027-01-31

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[ADR-016] listed two advisories in `src/frontend/driver-app/osv-scanner.toml` that the owner had not
yet confirmed, and [ADR-023] left them open:

- `GHSA-mrhx-c7xc-m3w5`, zod 3.25.76: memory exhaustion while validating oversized constrained
  collections. Rated High (CVSS 3.1 7.5, availability only). Every zod before 4.6.6 is affected,
  and 4.6.6 is the first fixed version.
- `GHSA-gcqx-w48m-w2h4`, deepmerge 4.3.1: prototype poisoning of merged objects. Rated Low (CVSS 3.1
  5.3, integrity only). 4.3.1 is the last affected version, and no fixed version exists.

Both are ignored until 2027-01-31, with the reasons the file gives. ADR-016 and that file said "the
only fix is zod 4". The advisory's data says 4.6.6, so this ADR corrects ADR-016 there, and the
file's reason is corrected with it.

The check, made on 2026-11-14 from `package-lock.json` and the installed packages:

- **zod 3.25.76** is needed by `@expo/cli` 58.1.17, by `eslint-plugin-react-hooks` 7.1.1 (dev only,
  through `eslint-config-expo`) and by `zod-validation-error` 4.0.2 (dev only).
- **In `@expo/cli`,** two files use zod, both at `expo start`. `DevToolsPlugin.schema.js` checks the
  manifests and command output of installed dev-tools plugins. `createMCPDevToolsExtensionSchema.js`
  builds the input schema for MCP tools, which arrive over a network tunnel only when
  `EXPO_UNSTABLE_MCP_SERVER` is set. No dev-tools plugin and no `expo-mcp` is installed, the
  variable is not set, and the MCP schema holds only scalars, so no tunnel input reaches zod's array
  code.
- **deepmerge 4.3.1** is needed by `@expo/config` 58.0.9 and by `jest-config` 30.5.2 (dev only).
- **`@expo/config`** is needed by `expo`, `@expo/cli`, `@expo/metro-config`,
  `@expo/prebuild-config` and `@expo/local-build-cache-provider`. Inside the `expo` package, four
  files import it, all Node code that runs at build time: `config/index.js`, `config/paths.js`,
  `scripts/resolveAppEntry.js` and `scripts/spm/app-target.js`. Nothing under `build/` or `src/`
  does, so the runtime entry `build/Expo.js` never loads it.
- **The app's own code** imports neither package, and the web export holds neither.

## Decision

The owner, Naim Elijah, accepts both advisories until 2027-01-31, as `osv-scanner.toml` records
them. At that date each is fixed, justified again or removed ([ADR-016]).

An `IgnoredVulns` entry matches the advisory's ID across the whole of the phone app's lockfile. Two
rules follow:
- any zod the app itself uses must be 4.6.6 or later;
- the app's code imports neither zod nor deepmerge unless it declares them in `package.json`, at a
  version this ADR allows.

## Consequences

- **Easy:** the audit stays green without a fork of Expo's tooling.
- **Hard:** the check is repeated at the expiry date, and whenever one of the triggers below fires.
- **Cost:** none now.
- **Risk:** an Expo release could move one of them into the app bundle. The check is repeated when
  `expo`, `@expo/cli` or `@expo/config` change.
- **Risk:** the entry hides the advisory for every zod in the lockfile. A zod older than 4.6.6
  declared for the app would pass the audit unseen. So would an import of the hoisted zod 3.25.76
  (`zod`, or its bundled `zod/v4`), because it changes no lockfile. The Zod 4 forms that
  [tech stack §10][TS-10] adopts for the web app at the Alpha, and [§11][TS-11] for the phone app in
  Semester B, must be 4.6.6 or later and declared. A lint rule that refuses undeclared imports,
  `import/no-extraneous-dependencies`, would close the second gap. It is not on yet.

## Alternatives considered

- **Override zod to 4.6.6 or later.** Rejected: `@expo/cli` declares `^3.25.76`.
- **Scope the zod entry to version 3.25.76** with a `[[PackageOverrides]]` entry instead of
  `[[IgnoredVulns]]`. That would keep reporting a vulnerable zod the app declares. But it would hide
  every advisory against 3.25.76, not just this one, and it would still miss an undeclared import.
  Not chosen now: the owner accepted the entries as they stand. It is the first option when the app
  adds its own zod.
- **Patch deepmerge.** Rejected: no fixed version exists.
- **Leave both pending.** Rejected: the owner has reviewed them.

## Revisit when

- The expiry date arrives.
- Expo accepts zod 4.6.6 or later (ADR-016's trigger, with the fixed version corrected).
- A fixed deepmerge is released.
- `expo`, `@expo/cli` or `@expo/config` changes in the lockfile, an SDK upgrade or a patch.
- A dev-tools plugin or `expo-mcp` is added, or `EXPO_UNSTABLE_MCP_SERVER` is set.
- Zod or deepmerge reaches the app's code or a runtime package, whether declared or imported
  without being declared. That includes the phone app's own Zod 4 for forms, which reopens this
  ADR to confirm it is 4.6.6 or later.
- The phone app's lockfile joins the pnpm workspace ([ADR-039]), or these entries move to another
  `osv-scanner.toml`. From then on they would also cover the web app's zod.

[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-023]: ADR-023-accept-minimatch-and-glob-advisories.md
[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[TS-10]: ../architecture/tech_stack.md#10-web-app
[TS-11]: ../architecture/tech_stack.md#11-phone-app
