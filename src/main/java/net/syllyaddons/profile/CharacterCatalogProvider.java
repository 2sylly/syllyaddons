package net.syllyaddons.profile;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface CharacterCatalogProvider {
    Optional<List<KnownCharacter>> currentCharacters();

    static CharacterCatalogProvider unavailable() {
        return Optional::empty;
    }
}
