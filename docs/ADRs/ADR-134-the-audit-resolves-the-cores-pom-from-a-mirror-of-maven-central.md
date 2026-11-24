# ADR-134: The audit resolves the core's pom from Google's mirror of Maven Central

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

`just audit` runs OSV-Scanner v2.7.0 over every lockfile and the core's `pom.xml` ([ADR-016]). The
CI `audit` job failed on 24 November 2026 while the code was fine ([#262][ISSUE-262]):

- run 45283320671 (`dev`, `ee3a341`) failed, and failed again when it was rerun 90 minutes later;
- run 45296526726 (`main`, `f36f2f7`) failed, then passed on a rerun two minutes later;
- runs 45296198977 and 45296272219 (`dev`, `7ea3fda`, the same tree as `main`) passed a few
  minutes earlier.

Each failure is the same request. To resolve the pom, the scan fetches its parent,
`spring-boot-starter-parent-4.1.2.pom`, from `repo.maven.apache.org`. Maven Central answered `429`
(rate limited), the scan reported "failed resolution for src/src/backend/core/pom.xml", and it
exited with 127, its code for a general error.

That resolution is worth keeping. [ADR-016] says OSV-Scanner skips the core's Java dependencies,
because their versions come from the parent. That no longer holds. When the parent loads, the scan
resolves the pom through it and checks **153 Maven packages with a version**, transitive ones
included, such as `hibernate-core` 7.4.13.Final, `spring-core` 7.0.10 and `tomcat-embed-core`
11.0.27. That count comes from `scan source -L pom.xml --format json --all-packages`, run on 24
November 2026. The 16 packages left without a version are the pom's own declarations, which the
scan filters as unscannable.

The pinned image offers two ways to change where Maven metadata comes from, both shown by
`scan source --help`: `--maven-registry <url>`, the default registry, and
`--data-source deps.dev|native`. It has no option to read a local Maven repository, such as CI's
Maven cache.

## Decision

Decided on 24 November 2026. This amends [ADR-016]'s command and its Java risk; everything else in
it stands.

1. **`just audit` passes `--maven-registry`, set to Google's mirror of Maven Central,**
   `https://maven-central.storage-download.googleapis.com/maven2`, held as `maven_mirror` in the
   `justfile`. The scan then fetches the parent and the BOMs from the mirror instead of
   `repo.maven.apache.org`. A pom that declares a repository of its own has that repository tried
   first: two of the 46 BOMs that `spring-boot-dependencies` 4.1.2 imports, `jackson-bom` 2.21.7 and
   3.1.7, declare Sonatype's snapshot repository. Both checks below ran on 24 November 2026:
   - **The same result.** The scan through the mirror found the same 153 packages and versions as
     the scan through Central, line for line.
   - **The same metadata that day.** Both hosts' `maven-metadata.xml` for
     `spring-boot-starter-parent` named 4.2.0 as the latest version, with the same `lastUpdated`,
     `20261119183517`. The mirror is not always that close: its index page says it copies Central
     12 times a day for its Americas bucket.
2. **The data source stays `deps.dev`,** the image's default, and the vulnerabilities still come
   from OSV.dev.
3. **The core's Java dependencies are audited,** as the scan resolves them. [ADR-016]'s "no Java
   dependency is checked today" is replaced by this. The CycloneDX SBOM of the core stays where
   [TS §20][TS-20] and [ADR-074] put it, at the pilot.

## Consequences

- **Easy:**
  - A rate limit at Maven Central no longer turns the audit red.
  - The Java packages are checked on every run, not only when Central answers.
  - It is one flag, with no new tool, plugin or build step, and `just audit` is the same locally
    and in CI.
- **Hard:** the scan resolves the pom with its own Maven resolver, not with Maven's. A version it
  picks can differ from the one the build uses where Maven's mediation is subtle. The SBOM at the
  pilot closes that.
- **Cost:** one more host the audit depends on.
- **Risk:**
  - The mirror can be up to about two hours behind Central, so a Dependabot bump to a parent
    released minutes ago could fail its first audit. A rerun after the mirror catches up passes.
  - Its index page says it "is not an officially supported Google product", so it has no service
    level. If Google retires it or rate-limits it, a parent it can't serve fails the audit loudly
    with exit 127, as today.
  - A BOM it can't serve fails nothing. deps.dev's resolver, which the scan uses, skips an imported
    BOM it can't fetch (`util/maven/dependency.go`), so the scan passes while checking the versions
    the declaring poms give instead of the versions that BOM manages.
  - The audit sends package names and versions to Google's hosts, as it already does to deps.dev and
    OSV.dev. It sends no company or personal data, so [NFR-209] is unaffected.

## Alternatives considered

- **Scan a CycloneDX SBOM that the build produces.** Rejected for now. It reads exactly what Maven
  resolves, from CI's Maven cache, but it adds a build plugin and Java to the audit job. [TS §20][TS-20]
  and [ADR-074] adopt CycloneDX at the pilot, and [TS §1][TS-1] rule 3 adopts nothing earlier.
- **Retry the scan when it exits 127.** Rejected: every attempt still asks Maven Central, and the
  rerun of 45283320671 failed 90 minutes after the first attempt.
- **`--no-resolve`.** Rejected: the scan would stop resolving the pom and would check none of the
  153 Java packages.
- **`--data-source native`.** Rejected: it fetches every pom in the graph from the registry, so it
  sends Maven Central more requests, not fewer.
- **Read CI's Maven cache.** Not possible: the pinned image has no option for a local repository.

## Revisit when

- An audit fails because the mirror is missing an artifact, or answers `429` or `5xx`: then move to
  the SBOM, or to another mirror.
- The pilot brings the core's CycloneDX SBOM: scan it instead of resolving the pom, and drop the
  flag.
- An OSV-Scanner upgrade adds an option to read a local Maven repository.

[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-074]: ADR-074-dependency-licences-checked-against-an-allow-list.md
[ISSUE-262]: https://github.com/NaimElijah/DispatchFlow/issues/262
[NFR-209]: ../requirements/requirements.md#nfr-209
[TS-1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS-20]: ../architecture/tech_stack.md#20-adoption-order
