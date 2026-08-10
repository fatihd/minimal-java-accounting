# Module dependencies

The architectural levels run from external API adapters at the top to
infrastructure adapters at the bottom. `app` and its neutral technical
`infrastructure` are deliberately grouped at the left. `shared.ports` provides
the common boundary vocabulary used by the implementation, while `app` and its
factories compose concrete runtime graphs. The diagram shows only the durable
adapter set and omits `app` fan-out plus some repetitive model or shared-port
arrows so the capability arrows remain readable.

[![Two-axis module architecture](ARCHITECTURE.svg)](ARCHITECTURE.svg)

The diagram keeps Gradle modules as solid boxes and shows logical ports as dashed
ports. `api`, `persistence`, and `messaging` remain meaningful architectural
boundaries even though their interfaces are typed uses of generic contracts
from the grey `shared.ports` module rather than separate Gradle subprojects.
Hollow triangle arrows identify implementations of those logical interfaces;
filled arrows identify ordinary use or module dependencies. Cylinders identify
external systems rather than modules. Box height
communicates architectural weight: adapters are 100 units high, ports are 67
units, and model, application, or technical-infrastructure modules are 150
units. `d.m.` abbreviates `dev.minimalaccounting`; Java package names omit
Gradle hyphens.

The durable selection has one adapter for each logical infrastructure port.
`adapters.messaging.outbox` implements messaging as one boundary spanning
transactional PostgreSQL staging and Artemis delivery.
`adapters.persistence.postgresql` implements persistence and depends on the
model because repository adapters reconstruct model aggregates. Both use the
neutral `shared.adapters.postgresql` technical foundation; neither feature
adapter depends on the other.

Regenerate the checked-in image with:

```shell
./gradlew renderArchitecture
```

## Direct production project dependencies

| Module | Direct project dependencies |
|---|---|
| `shared.ports` | — |
| `model` | — |
| `application` | `model`, `shared.ports` |
| `infrastructure` | `shared.ports` |
| `shared.adapters.postgresql` | `shared.ports` |
| `adapters.api.console` | `model`, `shared.ports` |
| `adapters.api.http` | `model`, `shared.ports` |
| `adapters.persistence.in-memory` | `model`, `shared.ports` |
| `adapters.persistence.postgresql` | `model`, `shared.ports`, `shared.adapters.postgresql` |
| `adapters.messaging.in-memory` | `shared.ports` |
| `adapters.messaging.outbox` | `shared.ports`, `shared.adapters.postgresql` |
| `app` | `shared.ports`, `infrastructure`, `application`, `model`, and all adapters |

Composition injects one `JdbcTransactions` context from
`shared.adapters.postgresql` into the repository and outbox adapters. That lets
repository changes and outbox inserts commit atomically without creating an
adapter-to-adapter dependency. The outbox adapter still implements the generic
messaging contracts and does not add messaging behavior to repositories.

`shared.ports` supplies the generic technical vocabulary: handlers, senders,
receivers, deliveries, repositories, and transaction boundaries. Domain
repositories use its `Repository<Identifier, Aggregate>` shape directly. The
`infrastructure` module consumes that vocabulary to provide JDK logging,
metrics, snapshots, and JMX decorators; `app` alone selects and composes
implementations.

An API adapter translates an external representation into the model
`SubmitTransaction` command and invokes `Handler<SubmitTransaction>`. An
infrastructure adapter implements a shared port for a concrete technology.
Application handlers implement use cases behind those boundaries; they are not
adapters. No adapter owns aggregate state transitions, and nothing depends on
`app`.
