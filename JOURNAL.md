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

## 2026-08-09 — Add the embedded HTTP transaction adapter

Added `adapters.api.http`, using the JDK HTTP server and adapter-local JSON
translation for `POST /transactions`. It returns asynchronous `202`, limits
request size, distinguishes transport errors from business rejection, and does
not expose unexpected failure details.

The server and virtual-thread executor are lifecycle objects built by an
explicit adapter factory. Socket-level tests cover successful translation,
method and media rejection, malformed and oversized bodies, business errors,
and internal failures. `./gradlew :adapters.api.http:test` completes
successfully on Java 25.

## 2026-08-09 — Add explicit console and server runtime modes

The executable now selects between the one-shot console and long-running HTTP
server. The memory server composes the same transaction processor, starts each
adapter poller on a virtual worker, registers the metrics MXBean, and closes
HTTP, workers, and JMX registration together.

`WorkerGroup` owns only lifecycle and receives poll functions from composition.
Startup account data remains configuration rather than API behavior.
`./gradlew test` completes successfully on Java 25.

## 2026-08-09 — Export messaging adapter contracts

`ports.messaging` now exports queue and topic behavior tests. Queue contracts
cover receive limits, ACK removal, and NACK redelivery. Topic contracts cover
independent subscriptions. They intentionally make no ordering or exactly-once
claim.

The in-memory adapter runs the contracts in addition to its focused poller
tests. Every later broker adapter must do the same.

## 2026-08-09 — Add PostgreSQL repositories

Added plain JDBC account, transaction, and journal repositories with a
checksummed, advisory-locked schema. They reconstruct detached aggregate
graphs, enforce optimistic revisions, preserve natural-key uniqueness, and run
the same explicit-update contract as the memory adapter.

An explicit JDBC transaction boundary reuses one connection for nested work.
Container tests cover rollback, stale updates, duplicate keys, child
reconstruction, removal, and idempotent schema installation. Normal `test`
remains Docker-free.

## 2026-08-09 — Add the transactional outbox

Added a PostgreSQL messaging adapter whose `Sender<T>` inserts typed JSON only
inside the active JDBC transaction. A transactional handler decorator places
the complete application handler around repository and outbox work.

The publisher locks pending rows with `SKIP LOCKED`, routes payloads to concrete
transport senders, and marks rows only after send success. The bridge is
explicitly at-least-once: business idempotency handles the broker-success and
database-rollback duplicate window. Container tests cover commit, rollback,
refusal outside a transaction, retry, route validation, and schema installation.

## 2026-08-09 — Add Apache Artemis messaging

Added an Artemis Core adapter without JMS or framework integration. Typed
queues implement sender and receiver ports. Typed topics implement a sender and
create durable independent subscription receivers. Each in-flight delivery
owns a small Core session so ACK and NACK remain independent within a batch.

The shared queue and topic contracts pass against Apache Artemis 2.55.0. An
additional container test round-trips a real business message containing
`Money` through adapter-local JSON serialization.
