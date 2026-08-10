package dev.minimalaccounting.shared.ports;

import java.util.Optional;

public interface Repository<Identifier, Aggregate> {
    Optional<Aggregate> get(Identifier identifier);

    Identifier add(Aggregate aggregate);

    void update(Aggregate aggregate);

    void remove(Identifier identifier);
}
