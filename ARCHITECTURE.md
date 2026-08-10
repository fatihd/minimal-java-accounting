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
filled arrows identify ordinary use or module dependencies. Box height
communicates architectural weight: adapters are 100 units high, ports are 67
units, and model, application, or technical-infrastructure modules are 150
units. `d.m.` abbreviates `dev.minimalaccounting`; Java package names omit
Gradle hyphens.

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
| `adapters.api.console` | `model`, `shared.ports` |
| `adapters.api.http` | `model`, `shared.ports` |
| `adapters.persistence.in-memory` | `model`, `shared.ports` |
| `adapters.persistence.postgresql` | `model`, `shared.ports` |
| `adapters.messaging.in-memory` | `shared.ports` |
| `adapters.messaging.artemis` | `shared.ports` |
| `adapters.messaging.postgresql` | `shared.ports`, `adapters.persistence.postgresql` |
| `app` | `shared.ports`, `infrastructure`, `application`, `model`, and all adapters |

The PostgreSQL messaging adapter depends on the PostgreSQL persistence adapter
only to share its active JDBC connection. That is what lets repository changes
and outbox inserts commit atomically. It still implements `Sender<T>` and does
not add messaging behavior to repository interfaces.

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
