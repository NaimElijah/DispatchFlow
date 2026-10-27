# DispatchFlow - Architecture Decision Records

One file per decision, named `ADR-NNN-short-kebab-title.md`. Records are append-only. To change
course, add a new ADR and mark the old one `Superseded by ADR-NNN`.

| ADR | Decision | Status |
|---|---|---|
| [ADR-004](ADR-004-pluggable-solver-port.md) | Every solver behind one solver port, chosen by name for each solve | Accepted |
| [ADR-005](ADR-005-or-tools-first-research-solver-for-re-optimization.md) | OR-Tools first, with our own solver as a measured research track aimed at re-optimization | Accepted |

> [!NOTE]
> Numbering restarted on 2026-10-27 and does not begin at ADR-001: ADR-004 keeps the number
> that `requirements.md` already cites. The requirements also cite 45 other records, numbered
> between ADR-003 and ADR-063, that are not in this folder.
