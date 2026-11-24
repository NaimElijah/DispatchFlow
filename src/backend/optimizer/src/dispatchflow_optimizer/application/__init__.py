"""The solve use cases, the `Solver` port and the solver registry (ADR-004, ADR-044, TS §7.1).

Declared ahead: each body raises `NotImplementedError` until it is built. The application
imports the domain, never a framework, a solver library or the process model, which live in the
adapters (ADR-001, ADR-051).
"""
