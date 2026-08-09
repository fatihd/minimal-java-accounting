# Module dependencies

The architectural layers are:

1. API adapters — `adapters.api.console`.
2. API ports — `ports.api`.
3. Core — `application` and `core`.
4. Infrastructure ports — `ports.persistence` and `ports.messaging`.
5. Infrastructure adapters — the two in-memory adapter modules.

`application` implements use cases and workflow coordination; it is not an
adapter. `app` is composition only, and `shared` is the small common foundation.

## Architecture grid

The five architectural layers run from the API adapter at the top to
infrastructure adapters at the bottom. Composition is leftmost and shared is
rightmost. To keep the boundary arrows readable, the grid omits the eight
composition-root arrows; the table below records every direct project
dependency.

[![Two-axis module architecture](ARCHITECTURE.svg)](ARCHITECTURE.svg)

The box heights deliberately communicate architectural weight: adapters are
100 units high, ports are 67 units (approximately two thirds), and core modules
are 150 units (one and a half times the adapter height). Each box pairs its
Gradle module with the corresponding abbreviated Java package root; `d.m.`
means `dev.minimalaccounting`. Java package segments omit Gradle-name hyphens,
so `in-memory` becomes `inmemory`. The checked-in SVG is generated without a
rendering library:

```shell
./gradlew renderArchitecture
```

## Direct Gradle project dependencies

| Module | Direct project dependencies |
|---|---|
| `shared` | — |
| `ports.api` | `shared` |
| `core` | `shared` |
| `ports.persistence` | `core` |
| `ports.messaging` | — |
| `application` | `ports.api`, `core`, `ports.persistence`, `ports.messaging` |
| `adapters.api.console` | `ports.api` |
| `adapters.persistence.in-memory` | `core`, `ports.persistence` |
| `adapters.messaging.in-memory` | `ports.messaging` |
| `app` | `ports.api`, `application`, `core`, both infrastructure ports, and all three adapters |

An API adapter translates an external protocol or interaction model into calls
on `ports.api`. An infrastructure adapter translates a repository or messaging
port to a concrete storage or delivery technology. Neither kind of adapter
owns business transitions. The composition root chooses concrete
implementations; nothing depends on `app`.
