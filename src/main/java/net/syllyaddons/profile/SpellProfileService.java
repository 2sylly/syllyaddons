package net.syllyaddons.profile;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.syllyaddons.domain.CharacterIdentity;
import net.syllyaddons.domain.ObservedState;

public final class SpellProfileService {
    private static final List<SpellBinding> FALLBACK_BINDINGS = List.of(
            new SpellBinding(new PhysicalInput(InputDevice.KEYSYM, 90), 1),
            new SpellBinding(new PhysicalInput(InputDevice.KEYSYM, 88), 2),
            new SpellBinding(new PhysicalInput(InputDevice.KEYSYM, 67), 3),
            new SpellBinding(new PhysicalInput(InputDevice.KEYSYM, 86), 4));

    private final SpellProfileStore store;
    private final SpellCastGateway castGateway;
    private final NativeSpellBindingProvider nativeBindings;
    private final CharacterCatalogProvider characterCatalog;
    private final SpellProfileResolver resolver = new SpellProfileResolver();
    private final Consumer<String> errorLogger;
    private final Consumer<String> profileChangeNotifier;

    private SpellProfileConfig config = SpellProfileConfig.empty();
    private String characterId;
    private String className;
    private String temporaryOverrideProfileId;
    private ProfileResolution activeResolution = ProfileResolution.none();
    private SpellCastResult lastCastResult;
    private String lastError = "";
    private boolean integrationAvailable = true;

    public SpellProfileService(
            SpellProfileStore store,
            SpellCastGateway castGateway,
            NativeSpellBindingProvider nativeBindings,
            Consumer<String> errorLogger) {
        this(
                store,
                castGateway,
                nativeBindings,
                CharacterCatalogProvider.unavailable(),
                errorLogger,
                ignored -> {});
    }

    public SpellProfileService(
            SpellProfileStore store,
            SpellCastGateway castGateway,
            NativeSpellBindingProvider nativeBindings,
            CharacterCatalogProvider characterCatalog,
            Consumer<String> errorLogger) {
        this(store, castGateway, nativeBindings, characterCatalog, errorLogger, ignored -> {});
    }

    public SpellProfileService(
            SpellProfileStore store,
            SpellCastGateway castGateway,
            NativeSpellBindingProvider nativeBindings,
            CharacterCatalogProvider characterCatalog,
            Consumer<String> errorLogger,
            Consumer<String> profileChangeNotifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.castGateway = Objects.requireNonNull(castGateway, "castGateway");
        this.nativeBindings = Objects.requireNonNull(nativeBindings, "nativeBindings");
        this.characterCatalog = Objects.requireNonNull(characterCatalog, "characterCatalog");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
        this.profileChangeNotifier = Objects.requireNonNull(profileChangeNotifier, "profileChangeNotifier");
    }

    public synchronized void initialize(ObservedState state) {
        integrationAvailable = true;
        try {
            config = store.load().orElseGet(SpellProfileConfig::empty);
        } catch (IOException | RuntimeException exception) {
            lastError = "Could not load spell profiles; using a fresh configuration";
            errorLogger.accept(lastError + ": " + exception.getMessage());
            config = SpellProfileConfig.empty();
        }
        pruneInvalidCharacterData();

        if (config.profiles().isEmpty()) {
            SpellProfile imported = new SpellProfile(
                    newId(), "Imported Wynntils", usableNativeBindings());
            config = new SpellProfileConfig(
                    SpellProfileConfig.CURRENT_SCHEMA_VERSION,
                    Map.of(imported.id(), imported),
                    Map.of(),
                    Map.of(),
                    imported.id(),
                    Map.of(),
                    Map.of(),
                    true);
            persist();
        }
        onObservedState(state);
        refreshCharacterCatalog();
    }

    public synchronized void onObservedState(ObservedState state) {
        Objects.requireNonNull(state, "state");
        if (!integrationAvailable) return;
        if (!state.character().isKnown()) {
            clearSession();
            return;
        }
        onCharacter(state.character().value());
    }

    public synchronized void onCharacter(CharacterIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (!integrationAvailable) return;
        if (!identity.id().equals(characterId)) temporaryOverrideProfileId = null;
        characterId = identity.id();
        KnownCharacter catalogCharacter = config.knownCharacters().get(identity.id());
        className = catalogCharacter != null && catalogCharacter.level() > 0
                ? catalogCharacter.className()
                : identity.className();
        rememberCharacter(identity);
        resolveActive();
    }

    public synchronized void clearSession() {
        characterId = null;
        className = null;
        temporaryOverrideProfileId = null;
        activeResolution = ProfileResolution.none();
        lastCastResult = null;
    }

    public synchronized OptionalInt spellForInput(PhysicalInput input) {
        if (!integrationAvailable || !activeResolution.resolved()) return OptionalInt.empty();
        return activeResolution.profile().spellFor(input);
    }

    public SpellCastResult castSpell(int spellNumber) {
        SpellCastResult result = castGateway.castSpell(spellNumber);
        synchronized (this) {
            lastCastResult = result;
            if (result == SpellCastResult.INTEGRATION_ERROR) {
                integrationAvailable = false;
                activeResolution = ProfileResolution.none();
                temporaryOverrideProfileId = null;
                lastError = "Wynntils spell integration failed closed; restart Minecraft";
                errorLogger.accept(lastError);
            }
        }
        return result;
    }

    public synchronized List<SpellProfile> profiles() {
        return config.profiles().values().stream()
                .sorted(Comparator.comparing(SpellProfile::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SpellProfile::id))
                .toList();
    }

    public synchronized Optional<SpellProfile> profile(String profileId) {
        return Optional.ofNullable(config.profiles().get(profileId));
    }

    public synchronized ProfileResolution activeResolution() {
        return activeResolution;
    }

    public synchronized String characterId() {
        return characterId;
    }

    public synchronized String className() {
        return className;
    }

    public synchronized SpellCastResult lastCastResult() {
        return lastCastResult;
    }

    public synchronized String lastError() {
        return lastError;
    }

    public synchronized SpellProfileConfig configSnapshot() {
        return config;
    }

    public synchronized List<KnownCharacter> knownCharacters() {
        return config.knownCharacters().values().stream()
                .sorted(Comparator.comparing((KnownCharacter character) -> !character.id().equals(characterId))
                        .thenComparing(KnownCharacter::className, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                character -> character.nickname() == null ? "" : character.nickname(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(KnownCharacter::id))
                .toList();
    }

    public synchronized boolean refreshCharacterCatalog() {
        Optional<List<KnownCharacter>> snapshot;
        try {
            snapshot = characterCatalog.currentCharacters();
        } catch (RuntimeException exception) {
            errorLogger.accept("Could not read the Wynntils character catalog: " + exception.getMessage());
            return false;
        }
        if (snapshot.isEmpty()) return false;

        Map<String, KnownCharacter> characters = resolveCatalogCharacters(snapshot.get());
        if (characters.isEmpty()) return false;

        if (characters.equals(config.knownCharacters())) return false;

        config = copyConfig(
                config.profiles(),
                config.characterAssignments(),
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                config.rememberedOverrides(),
                characters,
                config.automaticSwitchingEnabled());
        KnownCharacter current = characters.get(characterId);
        if (current != null) className = current.className();
        resolveActive();
        persist();
        return true;
    }

    public synchronized boolean linkCatalogCharacter(String provisionalId, String stableId) {
        KnownCharacter provisional = config.knownCharacters().get(provisionalId);
        KnownCharacter stableProbe = new KnownCharacter(stableId, "UNKNOWN");
        if (provisional == null || !stableProbe.hasStableId()) return false;

        KnownCharacter stable = new KnownCharacter(
                stableId, provisional.className(), provisional.nickname(), provisional.level());
        Map<String, KnownCharacter> characters = mutable(config.knownCharacters());
        characters.remove(provisionalId);
        characters.put(stableId, stable);

        Map<String, String> assignments = moveCharacterKey(config.characterAssignments(), provisionalId, stableId);
        Map<String, String> remembered = moveCharacterKey(config.rememberedOverrides(), provisionalId, stableId);
        config = copyConfig(
                config.profiles(),
                assignments,
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                remembered,
                characters,
                config.automaticSwitchingEnabled());
        if (stableId.equals(characterId)) className = stable.className();
        resolveActive();
        persist();
        return true;
    }

    public synchronized boolean automaticSwitchingEnabled() {
        return config.automaticSwitchingEnabled();
    }

    public synchronized void setAutomaticSwitchingEnabled(boolean enabled) {
        config = copyConfig(
                config.profiles(),
                config.characterAssignments(),
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                config.rememberedOverrides(),
                config.knownCharacters(),
                enabled);
        temporaryOverrideProfileId = null;
        resolveActive();
        persist();
    }

    public synchronized String assignedProfileId(String targetCharacterId) {
        return config.characterAssignments().get(targetCharacterId);
    }

    public synchronized void assignCharacter(String targetCharacterId, String profileId) {
        if (!config.knownCharacters().containsKey(targetCharacterId)) {
            throw new IllegalArgumentException("Unknown character " + targetCharacterId);
        }
        if (profileId != null) requireProfile(profileId);
        Map<String, String> assignments = mutable(config.characterAssignments());
        Map<String, String> remembered = mutable(config.rememberedOverrides());
        if (profileId == null) {
            assignments.remove(targetCharacterId);
        } else {
            assignments.put(targetCharacterId, profileId);
        }
        remembered.remove(targetCharacterId);
        config = copyConfig(
                config.profiles(),
                assignments,
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                remembered,
                config.knownCharacters(),
                config.automaticSwitchingEnabled());
        resolveActive();
        persist();
    }

    public synchronized String classFallbackProfileId(String targetClassName) {
        return config.classFallbacks().get(targetClassName);
    }

    public synchronized SpellProfile createProfile() {
        SpellProfile profile = new SpellProfile(newId(), uniqueName("New Profile"), List.of());
        putProfile(profile);
        return profile;
    }

    public synchronized SpellProfile duplicateProfile(String profileId) {
        SpellProfile source = requireProfile(profileId);
        SpellProfile duplicate = new SpellProfile(newId(), uniqueName(source.name() + " Copy"), source.bindings());
        putProfile(duplicate);
        return duplicate;
    }

    public synchronized SpellProfile importCurrentBindings() {
        SpellProfile imported = new SpellProfile(
                newId(), uniqueName("Imported Wynntils"), usableNativeBindings());
        putProfile(imported);
        return imported;
    }

    public synchronized SpellProfile resetToCurrentBindings(String profileId) {
        SpellProfile current = requireProfile(profileId);
        SpellProfile reset = new SpellProfile(current.id(), current.name(), usableNativeBindings());
        putProfile(reset);
        return reset;
    }

    public synchronized List<SpellBinding> currentNativeBindings() {
        return usableNativeBindings();
    }

    public synchronized void updateProfile(SpellProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (!config.profiles().containsKey(profile.id())) {
            throw new IllegalArgumentException("Unknown profile " + profile.id());
        }
        putProfile(profile);
    }

    public synchronized boolean deleteProfile(String profileId) {
        if (!config.profiles().containsKey(profileId)) return false;
        Map<String, SpellProfile> profiles = mutable(config.profiles());
        profiles.remove(profileId);

        Map<String, String> assignments = withoutValue(config.characterAssignments(), profileId);
        Map<String, String> fallbacks = withoutValue(config.classFallbacks(), profileId);
        Map<String, String> remembered = withoutValue(config.rememberedOverrides(), profileId);
        String global = profileId.equals(config.globalDefaultProfileId()) ? null : config.globalDefaultProfileId();
        if (profileId.equals(temporaryOverrideProfileId)) temporaryOverrideProfileId = null;

        config = copyConfig(
                profiles,
                assignments,
                fallbacks,
                global,
                remembered,
                config.knownCharacters(),
                config.automaticSwitchingEnabled());
        resolveActive();
        persist();
        return true;
    }

    public synchronized void select(String profileId, ManualSelectionMode mode) {
        requireProfile(profileId);
        Objects.requireNonNull(mode, "mode");
        switch (mode) {
            case TEMPORARY -> temporaryOverrideProfileId = profileId;
            case REMEMBERED -> {
                requireCharacter();
                Map<String, String> remembered = mutable(config.rememberedOverrides());
                remembered.put(characterId, profileId);
                config = copyConfig(
                        config.profiles(),
                        config.characterAssignments(),
                        config.classFallbacks(),
                        config.globalDefaultProfileId(),
                        remembered,
                        config.knownCharacters(),
                        config.automaticSwitchingEnabled());
                persist();
            }
            case ASSIGN_TO_CHARACTER -> {
                requireCharacter();
                Map<String, String> assignments = mutable(config.characterAssignments());
                assignments.put(characterId, profileId);
                Map<String, String> remembered = mutable(config.rememberedOverrides());
                remembered.remove(characterId);
                config = copyConfig(
                        config.profiles(),
                        assignments,
                        config.classFallbacks(),
                        config.globalDefaultProfileId(),
                        remembered,
                        config.knownCharacters(),
                        config.automaticSwitchingEnabled());
                persist();
            }
        }
        resolveActive();
    }

    public synchronized void setCurrentClassFallback(String profileId) {
        requireProfile(profileId);
        if (className == null) throw new IllegalStateException("No active character class");
        Map<String, String> fallbacks = mutable(config.classFallbacks());
        fallbacks.put(className, profileId);
        config = copyConfig(
                config.profiles(),
                config.characterAssignments(),
                fallbacks,
                config.globalDefaultProfileId(),
                config.rememberedOverrides(),
                config.knownCharacters(),
                config.automaticSwitchingEnabled());
        resolveActive();
        persist();
    }

    public synchronized void setGlobalDefault(String profileId) {
        requireProfile(profileId);
        config = copyConfig(
                config.profiles(),
                config.characterAssignments(),
                config.classFallbacks(),
                profileId,
                config.rememberedOverrides(),
                config.knownCharacters(),
                config.automaticSwitchingEnabled());
        resolveActive();
        persist();
    }

    private void putProfile(SpellProfile profile) {
        Map<String, SpellProfile> profiles = mutable(config.profiles());
        profiles.put(profile.id(), profile);
        config = copyConfig(
                profiles,
                config.characterAssignments(),
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                config.rememberedOverrides(),
                config.knownCharacters(),
                config.automaticSwitchingEnabled());
        resolveActive();
        persist();
    }

    private void resolveActive() {
        if (!config.automaticSwitchingEnabled() && temporaryOverrideProfileId == null) return;
        String currentProfileId = activeResolution.resolved() ? activeResolution.profile().id() : null;
        activeResolution = resolver.resolve(
                config, characterId, className, temporaryOverrideProfileId, currentProfileId);
        String resolvedProfileId = activeResolution.resolved() ? activeResolution.profile().id() : null;
        if (resolvedProfileId != null && !resolvedProfileId.equals(currentProfileId)) {
            try {
                profileChangeNotifier.accept(activeResolution.profile().name());
            } catch (RuntimeException exception) {
                errorLogger.accept("Could not show profile-change notification: " + exception.getMessage());
            }
        }
    }

    private void rememberCharacter(CharacterIdentity identity) {
        KnownCharacter existing = config.knownCharacters().get(identity.id());
        KnownCharacter known = new KnownCharacter(
                identity.id(),
                existing != null && existing.level() > 0 ? existing.className() : identity.className(),
                existing == null ? null : existing.nickname(),
                existing == null ? 0 : existing.level());
        if (!known.hasStableId()) return;
        if (known.equals(config.knownCharacters().get(identity.id()))) return;
        Map<String, KnownCharacter> characters = mutable(config.knownCharacters());
        characters.put(identity.id(), known);
        config = copyConfig(
                config.profiles(),
                config.characterAssignments(),
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                config.rememberedOverrides(),
                characters,
                config.automaticSwitchingEnabled());
        persist();
    }

    private void pruneInvalidCharacterData() {
        Map<String, KnownCharacter> characters = new LinkedHashMap<>();
        config.knownCharacters().forEach((id, character) -> {
            if (character != null && character.hasCatalogId()) characters.put(id, character);
        });
        Map<String, String> assignments = config.characterAssignments().entrySet().stream()
                .filter(entry -> isCatalogCharacterId(entry.getKey()))
                .collect(
                        LinkedHashMap::new,
                        (target, entry) -> target.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
        Map<String, String> remembered = config.rememberedOverrides().entrySet().stream()
                .filter(entry -> isCatalogCharacterId(entry.getKey()))
                .collect(
                        LinkedHashMap::new,
                        (target, entry) -> target.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
        if (characters.equals(config.knownCharacters())
                && assignments.equals(config.characterAssignments())
                && remembered.equals(config.rememberedOverrides())) {
            return;
        }
        config = copyConfig(
                config.profiles(),
                assignments,
                config.classFallbacks(),
                config.globalDefaultProfileId(),
                remembered,
                characters,
                config.automaticSwitchingEnabled());
        persist();
    }

    private SpellProfileConfig copyConfig(
            Map<String, SpellProfile> profiles,
            Map<String, String> assignments,
            Map<String, String> fallbacks,
            String global,
            Map<String, String> remembered,
            Map<String, KnownCharacter> characters,
            boolean automaticSwitchingEnabled) {
        return new SpellProfileConfig(
                SpellProfileConfig.CURRENT_SCHEMA_VERSION,
                profiles,
                assignments,
                fallbacks,
                global,
                remembered,
                characters,
                automaticSwitchingEnabled);
    }

    private List<SpellBinding> usableNativeBindings() {
        try {
            List<SpellBinding> bindings = nativeBindings.currentBindings();
            return bindings == null || bindings.isEmpty() ? FALLBACK_BINDINGS : List.copyOf(bindings);
        } catch (RuntimeException exception) {
            lastError = "Could not import Wynntils bindings; using Z/X/C/V";
            errorLogger.accept(lastError + ": " + exception.getMessage());
            return FALLBACK_BINDINGS;
        }
    }

    private void persist() {
        try {
            store.save(config);
            lastError = "";
        } catch (IOException exception) {
            lastError = "Could not save spell profiles";
            errorLogger.accept(lastError + ": " + exception.getMessage());
        }
    }

    private SpellProfile requireProfile(String profileId) {
        SpellProfile profile = config.profiles().get(profileId);
        if (profile == null) throw new IllegalArgumentException("Unknown profile " + profileId);
        return profile;
    }

    private void requireCharacter() {
        if (characterId == null) throw new IllegalStateException("No active character");
    }

    private String uniqueName(String base) {
        List<String> existing = config.profiles().values().stream()
                .map(SpellProfile::name)
                .map(String::toLowerCase)
                .toList();
        if (!existing.contains(base.toLowerCase())) return base;
        for (int suffix = 2; ; suffix++) {
            String candidate = base + " " + suffix;
            if (!existing.contains(candidate.toLowerCase())) return candidate;
        }
    }

    private static <K, V> Map<K, V> mutable(Map<K, V> source) {
        return new LinkedHashMap<>(source);
    }

    private static Map<String, String> withoutValue(Map<String, String> source, String value) {
        Map<String, String> copy = mutable(source);
        copy.values().removeIf(value::equals);
        return copy;
    }

    private Map<String, KnownCharacter> resolveCatalogCharacters(List<KnownCharacter> scanned) {
        Map<String, KnownCharacter> resolved = new LinkedHashMap<>();
        Set<String> linkedStableIds = new HashSet<>();
        for (KnownCharacter character : scanned) {
            if (character == null || !character.hasCatalogId()) continue;
            if (character.hasStableId()) {
                resolved.put(character.id(), character);
                linkedStableIds.add(character.id());
                continue;
            }

            List<KnownCharacter> matches = config.knownCharacters().values().stream()
                    .filter(KnownCharacter::hasStableId)
                    .filter(existing -> !linkedStableIds.contains(existing.id()))
                    .filter(existing -> sameMenuMetadata(existing, character))
                    .toList();
            if (matches.size() == 1) {
                KnownCharacter match = matches.getFirst();
                linkedStableIds.add(match.id());
                resolved.put(match.id(), new KnownCharacter(
                        match.id(), character.className(), character.nickname(), character.level()));
            } else {
                resolved.put(character.id(), character);
            }
        }
        return resolved;
    }

    private static boolean sameMenuMetadata(KnownCharacter first, KnownCharacter second) {
        return first.level() > 0
                && first.level() == second.level()
                && first.className().equalsIgnoreCase(second.className())
                && Objects.equals(normalizeNickname(first.nickname()), normalizeNickname(second.nickname()));
    }

    private static String normalizeNickname(String nickname) {
        return nickname == null ? null : nickname.toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, String> moveCharacterKey(Map<String, String> source, String from, String to) {
        Map<String, String> copy = mutable(source);
        String provisionalValue = copy.remove(from);
        if (provisionalValue != null) copy.put(to, provisionalValue);
        return copy;
    }

    private static boolean isCatalogCharacterId(String id) {
        try {
            return new KnownCharacter(id, "UNKNOWN").hasCatalogId();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
