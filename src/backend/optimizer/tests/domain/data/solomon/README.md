# Solomon instances and best-known solutions

Test data for the validator's acceptance of published best-known solutions (NFR-167), read by
`tests/domain/test_solomon_acceptance.py` through `vrplib`.

| Folder | Files | Source |
|---|---|---|
| `instances/` | `c101`, `r101`, `rc101`, `c201`, `r201`, `rc201` | SINTEF's 100-customer Solomon set: <https://www.sintef.no/globalassets/project/top/vrptw/solomon/solomon-100.zip> |
| `solutions/` | The best-known solution of each instance above | SINTEF's best-known solutions page: <https://www.sintef.no/projectweb/top/vrptw/100-customers/> |

- **Retrieved:** 2026-11-14.
- **Licence:** neither page states one. Committing these files was approved.
- **Changes:** line endings converted from CRLF to LF and trailing spaces removed. `vrplib`
  parses each file to the same values before and after.
- **Published distances**, which the test asserts the travel total rounds to: c101 828.94,
  r101 1650.80, rc101 1696.95, c201 591.56, r201 1252.37, rc201 1406.94.
