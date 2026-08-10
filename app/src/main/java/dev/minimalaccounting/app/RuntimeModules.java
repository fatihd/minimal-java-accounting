package dev.minimalaccounting.app;

sealed interface RuntimeModules permits InMemoryModules, DurableModules {
}

record InMemoryModules(
        InMemoryPersistenceModuleFactory persistence,
        InMemoryMessagingModuleFactory messaging) implements RuntimeModules {
}

record DurableModules(
        AccountingDestinations destinations,
        PostgresqlPersistenceModuleFactory persistence,
        PostgresqlMessagingModuleFactory outbox,
        ArtemisMessagingModuleFactory broker) implements RuntimeModules {
}
