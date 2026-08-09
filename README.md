# Minimal Java Accounting

A dependency-light accounting example with explicit ports and replaceable
adapters. The current runtime uses only concurrent JDK collections: no database,
ORM, broker, HTTP server, or dependency-injection framework.

The Gradle modules are deliberately coarse:

- `shared` contains only `Money`, `AccountNumber`, `DebitCredit`, and the shared
  posting-rejection exception under `dev.minimalaccounting.shared`. `Money` owns
  currency-safe arithmetic and comparison, while debit/credit owns its
  accounting polarity.
- `ports.api` contains the external `TransactionProcessor` API and its immutable
  transaction contract under `dev.minimalaccounting.ports.api`. It contains no
  core entities or technical DTO types.
- `core` contains entities, internal workflow messages, and pure accounting
  rules. It has no persistence or messaging dependency. Its packages are
  grouped by aggregate under `dev.minimalaccounting.core.account`,
  `.transaction`, and `.journal`. An aggregate package contains its inbound
  commands and outbound events as one protocol. The transaction package also
  contains its pure process manager because that workflow advances transaction
  state.
- `application` implements transaction intake, account-hold placement and
  release, journalization, and hold settlement. It performs repository and
  messaging I/O around core rules; it is not an adapter. Handlers are grouped
  by the aggregate they affect under `dev.minimalaccounting.application`:
  account command handlers under `.account`, journal creation under `.journal`,
  and event-driven transaction progress under `.transaction`.
- `ports.persistence` contains entity-specific repositories with only `get`,
  `add`, `update`, and `remove` operations. Its exported test fixtures define
  the explicit-update snapshot contract shared by persistence adapters.
- `ports.messaging` contains the generic `Sender<T>`, `Receiver<T>`,
  `Received<T>`, and `Handler<T>` ports. Receivers expose explicit `ack` and
  `nack` operations for their opaque transport message IDs.
- `adapters.api.console` translates one-shot command-line input into a call to
  `TransactionProcessor`. It is the console implementation paired with the
  `ports.api` boundary.
- `adapters.persistence.in-memory` and `adapters.messaging.in-memory` implement
  the infrastructure ports with concurrent JDK collections. Commands use
  queues; events use topics with independent named subscriptions.
- `app` is the executable composition root. It loads startup accounts, wires the
  concrete runtime, invokes the console adapter, and drives messaging until it
  is idle. Nothing depends on it.

The in-memory poller unwraps `Received<T>` and pushes the business message into
`Handler<T>`. Normal return ACKs the transport message ID; a runtime exception
NACKs it and stops the current batch. Application handlers are idempotent where
aggregate state permits it.

Internal commands are independent imperative records such as
`PlaceAccountHold`; events are independent completed facts such as
`AccountHoldPlaced`. Command and event types are never nested together, and
transport message IDs never appear in business payloads.

An account retains each hold as an owned lifecycle entity identified by
transaction ID and effect number. Duplicate placement, release, and settlement
commands therefore reconfirm their events without changing money twice. A late
placement cannot reopen a released or settled hold, conflicting payloads for one
hold identity fail, and release or settlement before placement throws so the
delivery is retried. Declined placements retain their reason and reconfirm the
same refusal on retry. Terminal hold records are deliberately retained as the
current in-memory idempotency history.

The persistence adapter composes its typed account, transaction, and journal
repositories from a small `InMemoryRepository`. That reusable implementation
owns generated IDs, optimistic revisions, and detached snapshots; typed
repositories own only aggregate copying and natural-key indexes. Mutating an
entity after `add`, `get`, or `update` never changes stored state without an
explicit `update`, matching the intended behavior of future durable adapters.

## Workflow responsibilities

An aggregate root validates a requested business change and mutates only its
own state. For example, `Transaction.placeEffect` advances the transaction and
its owned effect; it does not construct a release or journalization command.
`AccountEffect` exposes package-private lifecycle operations such as `hold`,
`beginRelease`, and `settle`; the transaction invokes those operations instead
of assigning child state. Aggregate mutators return no workflow instructions.
This keeps invariants and owned-entity mutation inside the root without making
the root responsible for messaging.

A command handler performs the I/O around one command: load its target
aggregate, invoke the matching business operation, persist the aggregate, and
publish the resulting event. `PlaceAccountHoldHandler`, for example, invokes
`Account.placeHold` and publishes either `AccountHoldPlaced` or the expected
business-refusal event `AccountHoldDeclined`. It does not choose the next
transaction command.

An asynchronous event handler loads the aggregate whose workflow the event
advances and constructs a short-lived process manager around it. The handler
passes in the event, persists or removes the changed aggregate, and mechanically
sends the commands selected by the process manager. Repository lookup,
transport delivery, retry behavior, and clocks therefore remain outside core.

`TransactionProcessManager` is a pure domain object for the workflow governed
by `TransactionState` and the transaction-owned `AccountEffectState` values. It
consumes relevant completed facts, invokes transaction state changes, and
derives typed next commands such as `ReleaseAccountHold`,
`JournalizeTransaction`, and `SettleAccountHold` from the resulting state. It
has no repository, sender, receiver, handler, clock, or transport dependency.
The transaction can be passed directly when a flow is synchronous, while an
asynchronous event handler can first rehydrate the same transaction and then use
the same process manager.

Transaction effect transitions distinguish duplicate, stale, premature, and
contradictory events. Duplicates preserve state and rederive only outstanding
commands, allowing a failed send after persistence to be retried. Stale events
from an earlier completed phase return without regression. Premature release or
settlement completion and mutually contradictory placed/declined results throw,
causing NACK instead of silently losing a causal transition.

Journalization is replay-safe across its non-atomic persistence/send boundary.
If a journal already exists for the transaction request, redelivery republishes
`TransactionJournalized` with the existing identity. A command or event whose
terminal transaction was removed is stale and ACKs. Missing accounts or a
missing journal for a still-present transaction throw for retry, while account
identity disagreement is rejected as inconsistent business state. Partial
settlement-command publication may reissue commands; account-owned hold
identity prevents duplicate monetary effects.

This split makes workflow decisions testable without adapters, preserves
aggregate encapsulation, and allows synchronous and asynchronous delivery to
share one business progression model. It is intentionally lighter than a
durable saga: there is no process repository, outbox, delivery history, or
atomic persistence-and-message commit yet. Those guarantees can be introduced
later through additional ports and adapters without moving business state into
persistence.

The transaction derives its owned effects when it starts from lines whose
account identities were resolved by the application. Cross-aggregate journal
calculation reads roots without mutating a second aggregate or exposing
transaction effects to application handlers.

Local invariants live with their values: transaction and journal lines validate
their own fields, and account balance bounds are represented by `BalanceLimits`.
Aggregate roots retain only lifecycle and cross-value rules such as balanced
transaction totals and projected account-balance policy.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the module boundaries, exact direct
dependencies, and checked-in SVG generated by a small dependency-free script.
See
[STATE-TRANSITIONS.md](STATE-TRANSITIONS.md) for the generated `Transaction`,
`AccountEffect`, and `AccountHold` lifecycle diagrams.

## Build and run

Use Java 25 and the checked-in Gradle wrapper:

```shell
sdk env
./gradlew test
./gradlew jacocoAggregateReport
./gradlew renderArchitecture
./gradlew renderStateTransitions
./gradlew :app:run --args='--help'
```

The aggregate coverage task runs every module's tests and writes its browsable
report to `build/reports/jacoco/aggregate/html/index.html`. It also writes XML
to `build/reports/jacoco/aggregate/report.xml` for programmatic analysis.

`examples/accounts.csv` is a startup file, not an account-management API. Submit
one transaction with:

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

Account files have this unquoted, six-column CSV shape; blank minimum and maximum
balances mean no limit:

```csv
account_number,name,opening_balance_minor,currency,minimum_balance_minor,maximum_balance_minor
DEBIT001,Debit account,1000,USD,,
CREDIT01,Credit account,1000,USD,,
```

Future HTTP, database, and broker integrations belong in new adapter modules.
They should replace composition choices without changing core business rules.
