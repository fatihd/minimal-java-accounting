# Design journal

This journal records stable architectural decisions and their validation.
Commands are relative to the repository root.

## 2026-08-08 — Establish the domain and module boundaries

Started with one `core` module for account, transaction, and journal business
state; a small `shared` module for boundary values; separate API, persistence,
and messaging ports; adapter modules per implementation family; and an outer
`app` composition root.

The external API has one business operation:
`TransactionProcessor.submit(Transaction)`. Account setup is startup
configuration, not a test operation. Repository interfaces retain only `get`,
`add`, `update`, and `remove`; key types carry lookup alternatives. Messaging
retains only send, receive, ACK/NACK, and push handler operations.

## 2026-08-08 — Keep lifecycle rules behind aggregate roots

Grouped core packages by account, transaction, and journal. Transaction starts
through a business factory and derives its owned `AccountEffect` children.
Application code does not assign child lifecycle states. Account hold placement,
release, and settlement remain behind `Account`; transaction effect progress
remains behind `Transaction`.

Local validation lives on the smallest owning domain type. Aggregate roots
retain cross-child and lifecycle invariants. Reproducible state diagrams under
`docs/state-transitions` document accepted transitions.

## 2026-08-08 — Separate handlers from workflow decisions

Command handlers perform one I/O unit: load the target aggregate, invoke its
operation, persist it, and emit the resulting event. Event handlers load the
aggregate whose workflow advances and delegate pure progression to
`TransactionProcessManager`.

The process manager has no repository, transport, handler, clock, or sender
dependency. Commands are imperative independent records; events are
completed-fact independent records. Focused tests cover duplicate, stale,
premature, contradictory, and out-of-order results.

## 2026-08-08 — Define explicit-update persistence semantics

The reusable in-memory repository generates identities and revisions while
storing defensive copies in concurrent JDK collections. Values passed to
`add` or `update`, and values returned by `get`, remain detached from stored
state. The exported repository contract verifies that mutation is invisible
until explicit update.

## 2026-08-09 — Add reproducible architecture, coverage, and CI

Added JaCoCo aggregate reports and dependency-free SVG renderers for module
architecture and state transitions. The architecture grid keeps composition at
the left, shared values at the right, API adapters at the top, infrastructure
adapters at the bottom, and ports between adapters and core logic.

The initial GitHub Actions workflow runs the Docker-free test suite on Java 25.
`./gradlew test renderArchitecture renderStateTransitions` completes
successfully.

## 2026-08-09 — Extract the in-memory composition factories

Moved repository, channel, handler, and poller construction out of
`InMemoryAccounting`. The runtime is now an assignment-only object, while typed
component records keep the graph explicit. `InMemoryAccountingFactory` selects
memory adapters and `AccountingComponentsFactory` constructs application
services once, providing the reuse point for later durable composition and
decorators.

`./gradlew test` completes successfully on Java 25.

## 2026-08-09 — Add boundary observability decorators

Added the standalone `observability` module. Generic decorators wrap the
transaction API, handlers, senders, and receivers with `System.Logger`,
concurrent counters, fixed latency histograms, immutable snapshots, and a
lifecycle-controlled JMX view. They log operation names and outcomes without
logging business payloads and propagate runtime failures unchanged.

The component factory applies decorators centrally; core and application
handlers remain unaware of observability. `./gradlew test` completes
successfully on Java 25.
