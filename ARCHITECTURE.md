# Module dependencies

The five architectural bands run from external API adapters at the top to
infrastructure adapters at the bottom. `app` is deliberately leftmost as the
composition root; `shared` is deliberately rightmost as the small common
foundation. The diagram omits `app`'s fan-out so the capability arrows remain
readable.

[![Two-axis module architecture](ARCHITECTURE.svg)](ARCHITECTURE.svg)

Arrows point from a module to its production project dependency. Box height
communicates architectural weight: adapters are 100 units high, ports are 67
units, and core/boundary-logic modules are 150 units. `d.m.` abbreviates
`dev.minimalaccounting`; Java package names omit Gradle hyphens.

Regenerate the checked-in image with:

```shell
./gradlew renderArchitecture
```

## Direct production project dependencies

| Module | Direct project dependencies |
|---|---|
| `shared` | — |
| `ports.api` | `shared` |
| `ports.messaging` | — |
| `core` | `shared` |
| `ports.persistence` | `core` |
| `application` | `ports.api`, `core`, `ports.persistence`, `ports.messaging` |
| `observability` | `ports.api`, `ports.messaging` |
| `adapters.api.console` | `ports.api` |
| `adapters.api.http` | `ports.api` |
| `adapters.persistence.in-memory` | `core`, `ports.persistence` |
| `adapters.persistence.postgresql` | `core`, `ports.persistence` |
| `adapters.messaging.in-memory` | `ports.messaging` |
| `adapters.messaging.artemis` | `ports.messaging` |
| `adapters.messaging.postgresql` | `ports.messaging`, `ports.persistence`, `adapters.persistence.postgresql` |
| `app` | every selectable runtime module plus `application`, `core`, and all ports |

The PostgreSQL messaging adapter depends on the PostgreSQL persistence adapter
only to share its active JDBC connection. That is what lets repository changes
and outbox inserts commit atomically. It still implements `Sender<T>` and does
not add messaging behavior to repository interfaces.

An API adapter translates an external representation into `ports.api`. An
infrastructure adapter translates persistence or messaging technology into the
corresponding infrastructure port. Application handlers implement use cases
behind those boundaries; they are not adapters. No adapter owns aggregate state
transitions, and nothing depends on `app`.
