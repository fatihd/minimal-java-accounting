# Minimal Java Accounting

Minimal Java Accounting is a small double-entry posting workflow built to make
ports, adapters, aggregate boundaries, retries, and durability visible. The
business model has no ORM, broker, HTTP, JSON, logging, metrics, or dependency
injection dependency. Those choices stay in outer infrastructure, adapter, and
composition modules.

The repository includes two complete runtimes:

- `memory` uses detached `ConcurrentHashMap` repositories plus in-memory queues
  and topics.
- `durable` uses plain JDBC PostgreSQL repositories and one outbox messaging
  adapter spanning transactional PostgreSQL staging and Apache Artemis Core
  queues and topics.

Both expose the same `Handler<SubmitTransaction>.handle(SubmitTransaction)` API
through a console adapter or the JDK HTTP server. This is a reference
implementation of the architectural boundaries and workflow rules, not a
production accounting product or a claim of exactly-once delivery.

## Architecture

[![Module architecture](ARCHITECTURE.svg)](ARCHITECTURE.svg)

The modules have intentionally narrow responsibilities:

- `shared.ports` contains the generic technical vocabulary used throughout the
  implementation: handlers, senders, receivers, deliveries, repositories, and
  transaction boundaries.
- `model` contains stable values such as `Money`, account, transaction, and
  journal aggregates, their commands and events—including the inbound
  `SubmitTransaction` command—pure posting rules, and the transaction process
  manager.
- `application` performs repository and messaging I/O around model operations.
  It implements use cases and handlers; it is not an adapter.
- `adapters.api.console` and `adapters.api.http` translate external protocols
  into `SubmitTransaction` and invoke its shared `Handler` port.
- `adapters.persistence.in-memory` and `adapters.persistence.postgresql`
  implement `Repository<Identifier, Aggregate>`. The shared test fixture
  exports their detached explicit-update contract.
- `adapters.messaging.in-memory` implements local queues, topics, and polling.
- `adapters.messaging.outbox` is the selected durable messaging adapter. It
  stages sends in PostgreSQL and delivers queues, topics, subscriptions, and
  polling through the Artemis Core client.
- `shared.adapters.postgresql` provides JDBC transactions, migration
  coordination, and failure translation reused independently by the durable
  messaging and persistence adapters.
- `infrastructure` provides boundary decorators backed by JDK logging,
  concurrent counters, latency histograms, immutable snapshots, and JMX.
- `app` is the only composition root. Nothing depends on it.

`shared.ports` provides the generic boundary vocabulary spoken by the
implementation. Composition is separate: `app` has one small factory context
for each runtime-contributing module. Factories selecting production-facing
HTTP, PostgreSQL, and outbox adapters live under `app.adapters.real`; console
and in-memory test/demo adapters live under `app.adapters.fake`. Each context
memoizes its objects independently, so an integration test can supply only the
dependencies used by the graph branch it requests.

Both families expose the same app-local persistence and messaging component
contracts. `ApplicationFactory` consumes those contracts without switching on
the selected implementation. Runtime-specific transaction, polling, outbox,
destination, and lifecycle behavior stays behind `RuntimeModules`. This keeps
adapter modules generic and makes cross-module wiring visible.

The overall factory composes handler observation with the selected runtime's
transaction wrapper and passes one type-preserving `HandlerWrapper` into the
application module factory. Each lazy handler is wrapped where it is created;
fake composition supplies `identity()` while durable composition adds the JDBC
transaction boundary. A small generic-method interface is used because one JDK
`Function<X, X>` cannot remain type-safe across heterogeneous `Handler<T>`
types.

Feature adapters do not depend on one another. Composition supplies shared
technical support to each adapter independently: the PostgreSQL persistence
adapter reconstructs model aggregates, while the outbox adapter spans the
database and broker resources required to implement durable messaging.

See [ARCHITECTURE.md](ARCHITECTURE.md) for exact production module
dependencies. See [STATE-TRANSITIONS.md](STATE-TRANSITIONS.md) for the generated
`Transaction`, `AccountEffect`, and `AccountHold` lifecycle diagrams.

## Business and workflow boundaries

Aggregate roots validate and mutate their own business state. `Account` owns
hold placement, release, and settlement. `Transaction` owns its state and
`AccountEffect` children; callers request operations such as `placeEffect` or
`settleEffect` rather than assigning child state.

A command handler loads one target aggregate, invokes its matching operation,
persists it, and emits the resulting event. An event handler loads the aggregate
whose workflow advances and constructs `TransactionProcessManager` around it.
The process manager consumes completed facts and derives the next typed commands
without repositories, senders, receivers, handlers, clocks, or transport IDs.

Commands are independent imperative records such as `PlaceAccountHold`. Events
are independent completed facts such as `AccountHoldPlaced`. Commands go to
queues; events go to topics with independent subscriptions. Transport IDs stay
in `Received<T>` and never enter business payloads.

Normal handler return means ACK. A runtime exception means NACK and retry.
Expected business refusal is represented by an event such as
`AccountHoldDeclined`, not an exception escaping the handler. Aggregate-owned
hold identity and transaction workflow state make duplicate and out-of-order
delivery safe wherever the business state can do so. Premature or contradictory
facts throw instead of silently skipping a causal transition.

On successful completion the workflow retains the journal entry and terminal
account holds, then removes the transient transaction. A repeated submission is
therefore recognized by either the active transaction request key or the
retained journal request key.

## Persistence and delivery semantics

Both repository implementations behave like explicit-update durable stores:
values passed to `add` or `update`, and values returned by `get`, are detached.
Changing an object does not alter stored state until `update` is called. Both
adapters run the same exported contract tests.

The durable composition wraps external submission and every asynchronous
handler in one shared transaction boundary. Its JDBC implementation makes
repository changes and outbox inserts commit
atomically. A separate publisher locks pending rows with `SKIP LOCKED`, sends
them to Artemis, and marks them published only after the broker send returns.

This is at-least-once delivery. A broker send can succeed immediately before a
database rollback, so a message may be delivered again. It cannot be made
exactly once by hiding retries in persistence; idempotent aggregate operations
and replay-safe application handlers are the intended recovery mechanism.

## Build and test

Use Java 25 and the checked-in Gradle wrapper:

```shell
./gradlew test
./gradlew integrationTest
./gradlew jacocoAggregateReport
./gradlew jacocoFullReport
./gradlew renderArchitecture renderStateTransitions
```

`test` is Docker-free. `integrationTest` starts pinned PostgreSQL 17.6 and
Apache Artemis 2.55.0 containers and runs repository contracts, shared queue/topic
contracts, outbox failure tests, and the durable HTTP restart flow.

The reports are browsable at:

- unit coverage: `build/reports/jacoco/aggregate/html/index.html`
- unit plus integration coverage: `build/reports/jacoco/full/html/index.html`
- durable runtime test: `app/build/reports/tests/integrationTest/index.html`

The checked-in SVGs are generated by dependency-free Node scripts. CI rejects a
diagram change that was not regenerated and uploads both coverage reports.

## Run in memory

`examples/accounts.csv` is composition configuration, not an account-management
API. Run the one-shot console flow with:

```shell
./gradlew :app:run --args="--accounts examples/accounts.csv submit \
  --request-id request-1 \
  --document-type TRANSFER \
  --document-number DOC-1 \
  --description demo \
  --occurred-at 2026-08-08T00:00:00Z \
  --line DEBIT001 DEBIT 100 USD 100 USD \
  --line CREDIT01 CREDIT 100 USD 100 USD"
```

Or start the memory HTTP runtime:

```shell
./gradlew :app:run --args='server --runtime memory --accounts examples/accounts.csv'
```

Submit through its only production operation:

```shell
curl --fail-with-body http://127.0.0.1:8080/transactions \
  -H 'Content-Type: application/json' \
  -d @examples/transaction.json
```

## Run durably

The local Compose file starts only infrastructure. Generate local credentials,
export runtime configuration, then run the same HTTP server with durable
adapters:

```shell
export ACCOUNTING_DATABASE_USER=accounting
export ACCOUNTING_DATABASE_PASSWORD="$(openssl rand -hex 24)"
export ACCOUNTING_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/accounting
export ACCOUNTING_BROKER_USER=accounting
export ACCOUNTING_BROKER_PASSWORD="$(openssl rand -hex 24)"
export ACCOUNTING_BROKER_URL=tcp://127.0.0.1:61616

docker compose up -d
./gradlew :app:run --args='server --runtime durable --accounts examples/accounts.csv'
```

`ACCOUNTING_DESTINATION_PREFIX` is optional and defaults to `accounting`. Use a
different prefix when several logical instances share one broker.

Metrics are available through the platform MBean server under
`dev.minimalaccounting:type=Metrics`. Boundary decorators log operation names
and outcomes without logging message payloads.

## Scope and extension points

The project’s long-term target is a compact, executable example showing that:

- business state and transitions can remain independent of storage and broker
  APIs;
- push handlers and pull receivers can share minimal messaging ports;
- durable retries can be explicit without embedding workflow logic in tables;
- boundary decoration can add logging and metrics around JDK functions, then
  adapt them to business ports without annotations or framework-managed
  proxies; and
- new HTTP, database, broker, LocalStack, or test adapters can be added as new
  modules rather than edits to model rules.

Likely next production-oriented extensions are dead-letter and poison-message
policy, outbox retention, broker reconnect tuning, database migration tooling,
health/read APIs in separate API ports, and load/chaos tests. These are omitted
until they demonstrate a boundary or business guarantee rather than merely add
framework surface area.
