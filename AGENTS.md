# Repository principles

## Build and history

- Use Java 25 and the checked-in Gradle wrapper.
- Run `./gradlew test` for the complete Docker-free build. Run
  `./gradlew integrationTest` when changing PostgreSQL, Artemis, the outbox, or
  durable composition.
- Lombok is a project-wide compile-time convention with no runtime dependency.
  Use `@NonNull` for required constructor parameters, method parameters, record
  components, and generated dependency constructors instead of handwritten
  `Objects.requireNonNull` boilerplate. Keep explicit null checks when validating
  nested or reconstructed aggregate state that no boundary annotation covers.
  The root `lombok.config` uses the JDK non-null check form so generated guards
  do not add artificial coverage branches. Do not add tests whose only purpose
  is exercising generated or handwritten null guards. Composition-factory
  dependencies are the exception: leave them unannotated so a test may provide
  only the dependencies needed by the lazy graph branch it requests.
- After changing module boundaries or the architecture layout, run
  `./gradlew renderArchitecture` and keep `ARCHITECTURE.svg` synchronized with
  `tools/render-architecture.mjs`.
- After changing a lifecycle state or transition, run
  `./gradlew renderStateTransitions` and keep the SVGs under
  `docs/state-transitions` synchronized with
  `tools/render-state-transitions.mjs`.
- Read `JOURNAL.md` before changing the design. Append a dated entry after each
  meaningful design, implementation, benchmark, or investigation step. Keep
  entries concise and avoid machine-specific paths, temporary failure logs, or
  credentials. Consolidate history only when explicitly requested for a
  release-history rewrite.
- Keep this repository self-contained. Do not import source, persistence
  design, test harnesses, or module layouts from unrelated implementations.

## Boundaries and modules

- Treat `shared.ports` as generic boundary vocabulary, not as composition. It
  owns only generic technical contracts. Do not add speculative shared modules
  before they have a distinct responsibility.
- Keep external commands business-named and immutable. They must never expose
  model entities, persistence state, workflow state, test probes, or technical
  names such as `Dto`, `Info`, or `Manager`. `SubmitTransaction` belongs beside
  the other transaction commands in `model.transaction`.
- The production API currently has one operation:
  `Handler<SubmitTransaction>.handle(SubmitTransaction)`.
- Keep stable business values and exceptions, entities, internal messages,
  state transitions, and pure business rules in the single `model` module.
  Model must not depend on persistence, messaging, protocols, frameworks, or
  adapters.
- Group model types under `dev.minimalaccounting.model.account`,
  `.transaction`, and `.journal`. Keep each aggregate's inbound commands and
  outbound events with that aggregate protocol. This protocol ownership is
  separate from application-handler placement, which follows the state or
  responsibility the handler advances.
- Keep meaningful state transitions on aggregate roots. Application handlers
  invoke business operations such as `account.placeHold` and
  `transaction.settleEffect`; they do not set entity fields or mutate owned
  children directly. An aggregate root may invoke intention-revealing business
  transitions on its owned entities; it must not assign their lifecycle enums.
  Keep direct state assignment limited to adapter reconstruction through
  explicit full-state constructors. Cross-aggregate domain services may
  calculate from read-only aggregate state, but mutate at most their explicit
  target root.
- Put local invariants and operations on the smallest domain type that owns
  them. Value objects own value arithmetic and comparison, and owned line types
  validate their own fields. Aggregate roots retain cross-child, lifecycle, and
  aggregate-wide rules; do not duplicate value-object rules in services.
- Aggregate operations validate and mutate aggregate-owned business state. They
  do not access repositories or messaging, construct follow-up commands, or
  return generic workflow-transition objects. Derive follow-up work separately
  from the resulting aggregate state.
- Expose aggregate-owned collections as read-only views. Child-entity mutation
  stays behind its aggregate root even though the domain model remains mutable.
- Account holds are account-owned lifecycle entities identified by transaction
  ID and effect number. Retain terminal hold records so duplicate place,
  release, and settle commands cannot apply monetary state twice. Conflicting
  reuse of a hold identity is an invariant failure; a terminal hold must never
  be reopened by a late command.
- Resolve referenced aggregate identities in the application before creating
  internal transaction lines. `Transaction.start(receivedAt)` derives its own
  `AccountEffect` children; no lifecycle service or application code constructs
  new business effects.
- Keep repository lookup in the application layer. Pass resolved aggregates to
  model business factories, which derive canonical identities and create new
  aggregate roots and owned entities without retaining or mutating the resolved
  aggregates. Do not pass repositories into model factories.
- Use business-creation factories to omit technical identity, revision, and
  workflow defaults. Full-state constructors remain available for adapter
  reconstruction; they are not application creation APIs.
- Put implementations of application services and workflow coordination in
  `application`. Application code may depend on model and `shared.ports`, but
  not concrete adapters or the technical `infrastructure` module.
- Keep `infrastructure` outside model and application. It owns dependency-light
  technical boundary decoration, logging, metrics, snapshots, and JMX. It
  consumes `shared.ports`; only the outer `app` composition selects it.
- Organize application handlers by the aggregate whose state they load and
  change. A command handler belongs with its target aggregate; an event handler
  belongs with the aggregate or application responsibility it advances, not
  automatically with the aggregate that raised the event.
- A command handler owns the I/O for one command: load its target aggregate,
  invoke the matching aggregate operation, persist the result, and publish the
  resulting event. It must not decide which command advances another
  aggregate's workflow.
- Keep a process manager in model beside the aggregate whose root state governs
  the workflow. Construct it around that aggregate; do not give it repositories,
  senders, receivers, handlers, clocks, transport IDs, or other I/O concerns.
  Pass time values and relevant facts into its methods. It consumes events,
  invokes aggregate transitions, and derives typed next commands from aggregate
  state without sending them.
- Keep asynchronous event handlers in application. They load the affected
  aggregate and any referenced data, construct the same pure process manager a
  synchronous caller could use, pass it the event, persist or remove the
  aggregate, and mechanically dispatch its commands. This lookup wrapper is not
  a second workflow implementation.
- Prefer one process manager for each aggregate-root workflow state machine.
  State on children owned by that root belongs to the same flow; do not create a
  separate process manager merely because a child has its own state enum.
- A process manager here is lightweight domain coordination, not a persisted
  saga. The durable runtime stores aggregate state and outgoing messages in one
  PostgreSQL transaction, but process state remains the aggregate state. Do not
  introduce a process repository or generic workflow table without a business
  state that actually requires one.
- Treat duplicate events as recovery opportunities: apply their aggregate
  transition idempotently and rederive only commands still outstanding from the
  persisted state. A stale event from an earlier completed phase must not
  regress state. A completion event that arrives before its causal transition,
  or mutually contradictory results for one effect, throws for NACK rather than
  being silently discarded.
- A port is a capability contract at a boundary. An adapter translates an
  external protocol or technology to or from that port. Implementing a use case
  behind an API port does not make the implementation an adapter.
- Put each implementation family in its own adapter Gradle module. New HTTP,
  RDBMS, LocalStack, ActiveMQ, or other integrations must be additional adapters,
  not changes to model rules.
- Select one adapter per logical port in a runtime composition. Name an adapter
  for the boundary implementation it provides, not necessarily for one vendor:
  an outbox messaging adapter may legitimately span a database and a broker.
- Feature adapters under `adapters.*` must not depend on one another. When two
  adapters need the same technology context, extract the smallest neutral
  support into `shared.adapters.<technology>` and inject it into both from
  `app`. Shared technical support is not itself the selected feature adapter.
- Draw external databases, brokers, and protocols as resources, not Gradle
  modules. Architecture dependency views must show adapter-to-model
  dependencies when an adapter reconstructs model objects, in addition to the
  adapter's logical-port implementation relationship.
- Match each Gradle module with its Java package root under
  `dev.minimalaccounting`: `model` for business types and stable values,
  `shared.ports` for generic boundary contracts, and
  `adapters.<capability>.<implementation>` for implementations. Preserve
  aggregate subpackages beneath `model` and
  `application`. Normalize a hyphenated
  Gradle segment by removing the hyphen in Java, as in `in-memory` to
  `inmemory`; do not create split packages across modules.
- Keep `app` as the outer composition root. It may select concrete adapters,
  bootstrap configured startup data, and control worker lifecycle. Nothing may
  depend on `app`.
- Represent each runtime-contributing Gradle module with an app-local module
  factory. Keep these factories app-local when their typed object graph uses
  accounting messages; generic adapter modules must not acquire model
  dependencies merely to simplify composition.
- Use `@RequiredArgsConstructor` for module-factory dependencies and
  `@Getter(lazy = true)` on each produced object. A factory getter memoizes one
  shared instance. Leave unused dependencies nullable so integration tests can
  construct only the graph branch they exercise.
- Keep lazy factory fields in topological order. When one produced field depends
  on another, call its getter; avoid nested object construction in field
  initializers. Prefer independent fields where the graph permits it.
- Module factories expose undecorated objects owned by their represented
  module. The overall `ApplicationFactory` currently owns cross-module joins,
  boundary decorators, transaction wrappers, pollers, outbox routes, and
  runtime assembly. Do not generalize cross-cutting-concern injection until a
  later design explicitly introduces it.

## Persistence and messaging

- Repository interfaces use collection vocabulary only: `get`, `add`, `update`,
  and `remove`. Domain-specific lookup variants belong in repository key types,
  not in extra repository methods. Use the generic
  `Repository<Identifier, Aggregate>` contract from `shared.ports` directly
  unless a repository genuinely adds another operation. Name compound keys
  `*Identifier`, not `*Reference`.
- Keep persistence coordination out of entities and business services. The
  in-memory repositories use defensive copies and concurrent JDK collections;
  the PostgreSQL repositories use plain JDBC and reconstruct equally detached
  aggregate graphs.
- Persistence adapters must behave like explicit-update stores: entities passed
  to `add` or `update`, and entities returned by `get`, are detached from stored
  state. Later mutation is invisible until `update` is called. Reuse the
  exported persistence contract tests for every mutable repository adapter; do
  not emulate dirty checking or a first-level cache in fakes.
- Shared messaging ports stay minimal: `Sender<T>.send`, `Receiver<T>.receive`,
  `Receiver<T>.ack`, `Receiver<T>.nack`, and `Handler<T>.handle`.
- Keep `TransactionBoundary` in `shared.ports` and make its primary operation
  return-capable. Its `Runnable` overload is only a convenience over the generic
  `Supplier<T>` operation.
- Keep transport identity outside business payloads. `Received<T>` carries an
  opaque receiver-scoped message ID solely for ACK/NACK.
- Use stable business identity, not transport delivery identity, for command
  idempotency. A successful retry must reconfirm the corresponding event without
  applying aggregate state again. A causally premature command throws for NACK
  and retry; a stale command against terminal business state returns normally.
- Model each command and event as an independent type. Commands use imperative
  business names; events use completed-fact names. Do not put commands and
  their resulting events under one sealed interface or outcome enum.
- Put the target aggregate ID first in a business message when that aggregate
  already exists. Prefer natural business English over mechanically forcing
  the aggregate name into an awkward type name.
- Deliver commands through queues and publish events to topics. Both directions
  use `Sender<T>`; each topic subscription exposes its own `Receiver<T>`.
- Pull adapters own pollers. A poller calls `receive` and pushes each message into
  a handler through `handle`; handlers never pull from queues.
- Normal handler return means ACK. A runtime exception means NACK and retry.
  Expected business refusal must be an event and a normal handler return, not a
  thrown runtime exception.
  Handlers and submission paths should be idempotent as far as their business
  state allows.
- In durable composition, wrap the complete external submission or message
  handler in the JDBC transaction boundary. Repository operations and
  PostgreSQL outbox sends must share that transaction. Never let a repository
  method publish directly.
- Treat the outbox-to-broker bridge as at-least-once. Mark an outbox row only
  after the broker send returns, use `SKIP LOCKED` for competing publishers,
  and rely on business idempotency for the send-success/database-rollback
  duplicate window. Do not claim exactly-once delivery.
- Keep broker destinations in composition. Commands use Artemis anycast queues;
  events use multicast addresses with one durable queue per subscription.
  Serialization belongs to messaging adapters, not model messages.
- An asynchronous command or event for a missing target may ACK as stale only
  when terminal removal makes that absence an expected state. Once the target
  exists, a missing referenced aggregate or journal is a consistency or
  visibility failure and must throw for retry. When a durable side effect
  already exists, redelivery republishes its event instead of recreating it.

## Runtime and tests

- API adapters parse and render their external representation only. They do not
  construct repositories, queues, handlers, pollers, or test data.
- Future API adapters should share one constructor-injected application object
  rather than recreate use-case wiring per entry point. Give adapters their
  application port and external configuration through constructors.
- Keep constructors to dependency and configuration assignment. Put startup
  parsing, object-graph construction, defaults, validation across components,
  and any other non-trivial creation logic in explicit composition factories.
- Implement technical boundary decoration once over JDK functional interfaces
  and adapt business-named ports through method references. Do not make domain
  ports extend `Consumer` or `Function` merely to simplify a decorator, and do
  not create one decorator class for every port shape.
- Test the shared application through its public API with injected adapters and
  deterministic dependencies. Give adapter implementations focused contract
  tests and give composition factories a small wiring smoke test. A trivial
  `main` method may be excluded from coverage; test it only when a subprocess
  smoke such as `--help`, invalid configuration, or clean startup/shutdown
  proves behavior not already covered below it.
- Do not expose account setup, repository inspection, journal lookup, queue
  depth, or drain controls through the transaction API merely to support tests.
- Keep all fixtures, account helpers, queue draining helpers, and state probes in
  test source sets. No test code or hard-coded demo transaction belongs in a main
  source set.
- Startup account files are composition configuration, not account-management
  API operations.
- Prefer focused tests for independently meaningful behavior. End-to-end tests
  call the real external API and keep any internal setup or inspection inside a
  test fixture.
- Keep `test` Docker-free. Put real PostgreSQL, broker, restart, and durable
  delivery tests in `integrationTest`; expose reusable behavioral contracts from
  the owning port module's test fixtures.
