package com.example.cobblemonitor;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket;
import ludichat.cobbreeding.BreedingUtilities;
import ludichat.cobbreeding.EggUtilities;
import ludichat.cobbreeding.PastureInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Detects egg presence transitions on explicitly registered pasture blocks. */
public final class PastureEggNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final String PASTURE_BLOCK_ID = "cobblemon:pasture";
    private static final String HAS_EGG_PROPERTY = "has_egg";
    private static final String PART_PROPERTY = "part";
    private static final String TOP_PART = "top";
    private static final int LOOK_TARGET_VERTICAL_FALLBACK = 2;
    private static final long OPEN_PASTURE_PACKET_TIMEOUT_TICKS = 100L;

    private final ConfigManager configManager;
    private NotificationService notificationService;
    private final Map<String, Boolean> observedStates = new java.util.HashMap<>();
    private final Map<String, List<CachedParentSpecies>> guiCachedParents = new java.util.HashMap<>();
    /**
     * Keys whose parents came from a packet in this session rather than from the
     * config, so the debug report can tell a live capture from a restored one.
     */
    private final Set<String> parentsCachedThisSession = new java.util.HashSet<>();
    /**
     * Counts pasture packets dropped because no GUI was associated. The ignore path
     * is otherwise silent, which made it impossible to tell from a log whether the
     * condition ever actually fires.
     */
    private int ignoredPastureUpdates;
    private String pendingGuiPastureKey;
    private String activeGuiPastureKey;
    private long pendingGuiPastureExpiresAt;
    private String lastOpenPasturePacket = "none";
    private String lastOpenPastureCacheStatus = "none";
    private String lastPastureCacheUpdate = "none";

    public PastureEggNotifier(ConfigManager configManager, NotificationService notificationService) {
        this.configManager = configManager;
        this.notificationService = notificationService;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void resetWorldState() {
        observedStates.clear();
        ignoredPastureUpdates = 0;
        pendingGuiPastureKey = null;
        activeGuiPastureKey = null;
        pendingGuiPastureExpiresAt = 0L;
        lastOpenPasturePacket = "none";
        lastOpenPastureCacheStatus = "none";
        lastPastureCacheUpdate = "none";
        // Parents are restored rather than dropped: a pasture the player never
        // reopens would otherwise lose its egg species on every world change.
        reloadPersistedParents();
    }

    /** Rebuilds the in-memory parent cache from the persisted configuration. */
    public void reloadPersistedParents() {
        guiCachedParents.clear();
        parentsCachedThisSession.clear();
        int restored = 0;
        for (ConfigManager.MonitoredPasture target : configManager.getConfig().monitoredPastures) {
            List<CachedParentSpecies> parents = new ArrayList<>();
            for (ConfigManager.CachedParent stored : target.cachedParents) {
                CachedParentSpecies parent = fromStoredParent(stored);
                if (parent != null) {
                    parents.add(parent);
                }
            }
            if (!parents.isEmpty()) {
                guiCachedParents.put(configPastureKey(target), parents);
                restored++;
            }
        }
        if (restored > 0) {
            LOGGER.info("Restored persisted pasture parents for {} pasture(s)", restored);
        }
    }

    /** Arms a one-shot association before Cobblemon opens a pasture GUI. */
    public void markPastureInteraction(ClientWorld world, BlockPos interactedPos) {
        activeGuiPastureKey = null;
        BlockPos base = resolvePastureBase(world, interactedPos);
        if (base == null) {
            return;
        }
        String dimension = world.getRegistryKey().getValue().toString();
        boolean monitored = configManager.getConfig().monitoredPastures.stream().anyMatch(target ->
                dimension.equals(target.dimension)
                        && base.equals(resolvePastureBase(world, new BlockPos(target.x, target.y, target.z)))
        );
        if (monitored) {
            pendingGuiPastureKey = pastureKey(dimension, base);
            pendingGuiPastureExpiresAt = world.getTime() + OPEN_PASTURE_PACKET_TIMEOUT_TICKS;
        }
    }

    /** Caches the parent species Cobblemon sends only while a pasture GUI is opened. */
    public void cacheOpenedPasture(ClientWorld world, OpenPasturePacket packet) {
        List<CachedParentSpecies> parents = new ArrayList<>();
        List<String> species = new ArrayList<>();
        for (OpenPasturePacket.PasturePokemonDataDTO data : packet.getTetheredPokemon()) {
            CachedParentSpecies parent = toCachedParent(data);
            parents.add(parent);
            species.add(parent.displayName());
        }
        lastOpenPasturePacket = "pastureId=" + packet.getPastureId()
                + ", DTOs=" + species.size()
                + ", species=" + (species.isEmpty() ? "none" : String.join(", ", species));
        if (pendingGuiPastureKey == null || world.getTime() > pendingGuiPastureExpiresAt) {
            lastOpenPastureCacheStatus = "ignored: no pending monitored pasture interaction";
            return;
        }
        guiCachedParents.put(pendingGuiPastureKey, parents);
        parentsCachedThisSession.add(pendingGuiPastureKey);
        persistParents(pendingGuiPastureKey, parents);
        activeGuiPastureKey = pendingGuiPastureKey;
        LOGGER.info("Cached {} pasture parent species from OpenPasturePacket", species.size());
        lastOpenPastureCacheStatus = "cached for " + pendingGuiPastureKey;
        pendingGuiPastureKey = null;
        pendingGuiPastureExpiresAt = 0L;
    }

    /** Updates the active monitored pasture cache when Cobblemon adds a Pokemon in its GUI. */
    public void cachePasturedPokemon(OpenPasturePacket.PasturePokemonDataDTO data) {
        if (activeGuiPastureKey == null) {
            ignoredPastureUpdates++;
            lastPastureCacheUpdate = "ignored PokemonPasturedPacket: no active monitored pasture GUI";
            return;
        }
        List<CachedParentSpecies> cached = new ArrayList<>(guiCachedParents.getOrDefault(activeGuiPastureKey, List.of()));
        cached.removeIf(parent -> Objects.equals(parent.pokemonId(), data.getPokemonId()));
        cached.add(toCachedParent(data));
        guiCachedParents.put(activeGuiPastureKey, cached);
        parentsCachedThisSession.add(activeGuiPastureKey);
        persistParents(activeGuiPastureKey, cached);
        lastPastureCacheUpdate = "PokemonPasturedPacket cached for " + activeGuiPastureKey;
        LOGGER.info("Pasture cache updated using PokemonPasturedPacket");
    }

    /** Updates the active monitored pasture cache when Cobblemon removes a Pokemon in its GUI. */
    public void cacheUnpasturedPokemon(UUID pokemonId) {
        if (activeGuiPastureKey == null) {
            ignoredPastureUpdates++;
            lastPastureCacheUpdate = "ignored PokemonUnpasturedPacket: no active monitored pasture GUI";
            return;
        }
        List<CachedParentSpecies> cached = new ArrayList<>(guiCachedParents.getOrDefault(activeGuiPastureKey, List.of()));
        cached.removeIf(parent -> Objects.equals(parent.pokemonId(), pokemonId));
        guiCachedParents.put(activeGuiPastureKey, cached);
        parentsCachedThisSession.add(activeGuiPastureKey);
        persistParents(activeGuiPastureKey, cached);
        lastPastureCacheUpdate = "PokemonUnpasturedPacket cached for " + activeGuiPastureKey;
        LOGGER.info("Pasture cache updated using PokemonUnpasturedPacket");
    }

    /** Prevents later pasture packets from being associated after the GUI closes. */
    public void closePastureGui() {
        activeGuiPastureKey = null;
    }

    /**
     * Writes the cache through to disk. A miss is normal and harmless: only a
     * registered pasture can be cached, but its stored position is matched exactly,
     * so a legacy entry recorded on the pasture's top half stays session-only.
     */
    private void persistParents(String key, List<CachedParentSpecies> parents) {
        for (ConfigManager.MonitoredPasture target : configManager.getConfig().monitoredPastures) {
            if (!configPastureKey(target).equals(key)) {
                continue;
            }
            List<ConfigManager.CachedParent> stored = new ArrayList<>();
            for (CachedParentSpecies parent : parents) {
                stored.add(new ConfigManager.CachedParent(
                        parent.pokemonId() == null ? "" : parent.pokemonId().toString(),
                        parent.speciesId().toString(),
                        new ArrayList<>(parent.aspects())
                ));
            }
            target.cachedParents = stored;
            configManager.save();
            LOGGER.info("Persisted {} pasture parent(s) for {}", stored.size(), key);
            return;
        }
    }

    /** Resolves a stored parent, translating its name through the current language. */
    private static CachedParentSpecies fromStoredParent(ConfigManager.CachedParent stored) {
        Identifier speciesId = Identifier.tryParse(stored.species);
        if (speciesId == null) {
            LOGGER.debug("Ignoring a persisted pasture parent with an unreadable species id");
            return null;
        }
        Set<String> aspects = Set.copyOf(stored.aspects);
        Species resolved = PokemonSpecies.getByIdentifier(speciesId);
        String displayName = resolved == null
                ? speciesId.getPath()
                : formatSpeciesDisplay(resolved, aspects);
        return new CachedParentSpecies(parseUuid(stored.pokemonId), speciesId, aspects, displayName);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Uses the registered position, which the add command already stores as the base. */
    private static String configPastureKey(ConfigManager.MonitoredPasture target) {
        return pastureKey(target.dimension, new BlockPos(target.x, target.y, target.z));
    }

    private static CachedParentSpecies toCachedParent(OpenPasturePacket.PasturePokemonDataDTO data) {
        Species resolved = PokemonSpecies.getByIdentifier(data.getSpecies());
        Set<String> aspects = data.getAspects() == null ? Set.of() : Set.copyOf(data.getAspects());
        String displayName = resolved == null
                ? data.getSpecies().getPath()
                : formatSpeciesDisplay(resolved, aspects);
        return new CachedParentSpecies(data.getPokemonId(), data.getSpecies(), aspects, displayName);
    }

    public void tick(MinecraftClient client) {
        if (client.world == null || !configManager.getConfig().events.pastureEgg) {
            return;
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        for (ConfigManager.MonitoredPasture target : configManager.getConfig().monitoredPastures) {
            if (!target.dimension.equals(dimension)) {
                continue;
            }

            BlockPos registeredPos = new BlockPos(target.x, target.y, target.z);
            if (!client.world.isChunkLoaded(registeredPos)) {
                continue;
            }
            BlockPos pos = resolvePastureBase(client.world, registeredPos);
            if (pos == null) {
                continue;
            }
            if (!client.world.isChunkLoaded(pos)) {
                continue;
            }

            Boolean hasEgg = readHasEgg(client.world, pos);
            if (hasEgg == null) {
                continue;
            }

            String key = target.dimension + ":" + pos.toShortString();
            Boolean previous = observedStates.putIfAbsent(key, hasEgg);
            if (previous != null && !previous && hasEgg) {
                onEggCreated(client.world, pos, target);
            }
            observedStates.put(key, hasEgg);
        }
    }

    private void onEggCreated(ClientWorld world, BlockPos pos, ConfigManager.MonitoredPasture target) {
        EggMetadata metadata = readEggMetadata(world, pos, true);

        LOGGER.info("Pasture detected using BlockState");
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        if (metadata.species.isEmpty()) {
            addInferredEggSpecies(fields, metadata, pos);
        } else {
            fields.put("Species", String.join(", ", metadata.species));
            if (metadata.species.size() == 1) {
                addPokemonThumbnail(fields, metadata.species.iterator().next());
            }
        }
        if (!metadata.parents.species.isEmpty()) {
            fields.put("Parents", String.join(" + ", metadata.parents.species));
        }
        if (!metadata.parents.usable) {
            fields.put(
                    "Parent Data",
                    "Open this pasture once to include parent and inferred egg species."
            );
        }
        fields.put("Pasture", world.getRegistryKey().getValue() + " " + pos.toShortString());
        if (target.registeredByName != null && !target.registeredByName.isBlank()) {
            fields.put("Registered By", target.registeredByName);
        }
        notificationService.notify(NotificationService.EventType.PASTURE_EGG, fields);
    }

    private EggMetadata readEggMetadata(ClientWorld world, BlockPos pos, boolean eggExpected) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        ParentMetadata parents = readPastureParents(world, pos, blockEntity);
        if (!(blockEntity instanceof PastureInventory inventory)) {
            return new EggMetadata(Set.of(), 0, false, parents);
        }

        Set<String> species = new LinkedHashSet<>();
        int eggCount = 0;
        for (ItemStack stack : inventory.getItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            eggCount += stack.getCount();
            try {
                PokemonProperties properties = EggUtilities.extractProperties(stack);
                if (properties != null && properties.getSpecies() != null && !properties.getSpecies().isBlank()) {
                    species.add(properties.getSpecies());
                }
            } catch (RuntimeException exception) {
                LOGGER.debug("Could not read pasture egg metadata", exception);
            }
        }
        boolean inventoryContentsSynced = !eggExpected || eggCount > 0;
        if (eggExpected && !inventoryContentsSynced) {
            LOGGER.debug("Pasture at {} has HAS_EGG=true but holds no client-visible egg ItemStacks."
                    + " Cobblemon never sends pasture contents to a client, so this is the normal case,"
                    + " not a synchronization delay", pos);
        }
        return new EggMetadata(species, eggCount, inventoryContentsSynced, parents);
    }

    /**
     * Uses Cobbreeding's own possible-egg calculation instead of duplicating Ditto,
     * gender, form, and egg-group rules in this client mod.
     */
    private ParentMetadata readPastureParents(ClientWorld world, BlockPos pos, BlockEntity blockEntity) {
        String key = pastureKey(world.getRegistryKey().getValue().toString(), pos);
        List<CachedParentSpecies> cachedParents = guiCachedParents.get(key);
        // Restored entries are as usable as freshly captured ones, but reporting both
        // as "OpenPasturePacket cache" would claim a packet arrived this session.
        String cacheSource = parentsCachedThisSession.contains(key)
                ? "OpenPasturePacket cache"
                : "persisted cache (restored from config)";
        if (!(blockEntity instanceof PokemonPastureBlockEntity pasture)) {
            return fromGuiParentCache(cachedParents, cacheSource);
        }

        int tetheredEntryCount = pasture.getTetheredPokemon().size();
        if (tetheredEntryCount == 0) {
            return fromGuiParentCache(cachedParents, tetheredEntryCount, cacheSource);
        }
        try {
            List<Pokemon> parents = BreedingUtilities.getPokemon(pasture.getTetheredPokemon());
            List<String> parentSpecies = new ArrayList<>();
            for (Pokemon parent : parents) {
                parentSpecies.add(parent.getSpecies().getName());
            }

            Set<String> possibleSpecies = readPossibleEggSpecies(parents);
            return new ParentMetadata(
                    parentSpecies,
                    possibleSpecies,
                    tetheredEntryCount,
                    parents.size(),
                    true,
                    "BlockEntity",
                    null,
                    null
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.debug("Could not infer pasture egg species from tethered Pokemon", exception);
            return fromGuiParentCache(cachedParents, tetheredEntryCount, cacheSource);
        }
    }

    /** Uses GUI-cached species only when the current BlockEntity has no parent data. */
    private static ParentMetadata fromGuiParentCache(
            List<CachedParentSpecies> cachedParents,
            String cacheSource
    ) {
        return fromGuiParentCache(cachedParents, 0, cacheSource);
    }

    private static ParentMetadata fromGuiParentCache(
            List<CachedParentSpecies> cachedParents,
            int tetheredEntryCount,
            String cacheSource
    ) {
        if (cachedParents == null || cachedParents.isEmpty()) {
            return new ParentMetadata(List.of(), Set.of(), tetheredEntryCount, 0, false, "unavailable", null, null);
        }
        InferredEggSpecies inferred = inferEggSpeciesFromGuiParents(cachedParents);
        return new ParentMetadata(
                cachedParents.stream()
                        .map(CachedParentSpecies::displayName)
                        .toList(),
                inferred.names,
                tetheredEntryCount,
                cachedParents.size(),
                true,
                cacheSource,
                inferred.species,
                inferred.form
        );
    }

    /**
     * This only infers when the GUI cache describes a completed breeding pair:
     * one non-Ditto species paired with Ditto, or a same-species pair. The egg's
     * existence is already confirmed by HAS_EGG before this metadata is used.
     */
    private static InferredEggSpecies inferEggSpeciesFromGuiParents(List<CachedParentSpecies> parentNames) {
        // A full pasture may contain more residents than the actual breeding pair.
        // Do not guess an egg species unless the GUI reports exactly the two parents.
        if (parentNames.size() != 2) {
            return InferredEggSpecies.unavailable();
        }
        Set<Species> parentSpecies = new LinkedHashSet<>();
        List<ResolvedCachedParent> nonDitto = new ArrayList<>();
        List<ResolvedCachedParent> resolvedParents = new ArrayList<>();
        boolean hasDitto = false;
        for (CachedParentSpecies parent : parentNames) {
            Species species = PokemonSpecies.getByIdentifier(parent.speciesId());
            if (species == null) {
                return InferredEggSpecies.unavailable();
            }
            ResolvedCachedParent resolvedParent = new ResolvedCachedParent(
                    species,
                    resolveForm(species, parent.aspects())
            );
            resolvedParents.add(resolvedParent);
            parentSpecies.add(species);
            if ("ditto".equals(species.getResourceIdentifier().getPath())) {
                hasDitto = true;
            } else {
                nonDitto.add(resolvedParent);
            }
        }
        boolean sameSpeciesPair = parentSpecies.size() == 1 && !hasDitto;
        if (nonDitto.isEmpty() && hasDitto && parentSpecies.size() == 1) {
            ResolvedCachedParent ditto = resolvedParents.getFirst();
            return InferredEggSpecies.of(ditto.species(), ditto.form());
        }
        boolean validDittoPair = hasDitto && nonDitto.size() == 1;
        boolean validSameSpeciesPair = !hasDitto && sameSpeciesPair && nonDitto.size() == 2;
        if (!validDittoPair && !validSameSpeciesPair) {
            return InferredEggSpecies.unavailable();
        }
        if (!hasDitto && !sameForm(nonDitto)) {
            // The GUI DTO has no gender, so mixed-form same-species pairs cannot
            // reveal which parent determines the egg form without guessing.
            return InferredEggSpecies.unavailable();
        }
        ResolvedCachedParent breedingParent = nonDitto.getFirst();
        Species breedingSpecies = breedingParent.species();
        FormData breedingForm = breedingParent.form() == null
                ? breedingSpecies.getStandardForm()
                : breedingParent.form();
        try {
            FormData baby = BreedingUtilities.INSTANCE.getBaby(breedingForm);
            if (baby != null && baby.getSpecies() != null) {
                return InferredEggSpecies.of(baby.getSpecies(), baby);
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not resolve the baby species for cached pasture parents", exception);
        }
        // The server confirmed HAS_EGG. Preserve a useful, deterministic result
        // even if Cobbreeding's baby lookup cannot resolve a special form.
        return InferredEggSpecies.of(breedingSpecies, breedingForm);
    }

    /**
     * Cobbreeding's public Java signature exposes Kotlin Pair in its generic
     * return type. Read only the FormData map keys reflectively so this optional
     * integration remains compile-only and does not bundle Kotlin dependencies.
     */
    private static Set<String> readPossibleEggSpecies(List<Pokemon> parents) throws ReflectiveOperationException {
        Object candidates = BreedingUtilities.class
                .getMethod("getPossibleEggs", List.class)
                .invoke(null, parents);
        Set<String> possibleSpecies = new LinkedHashSet<>();
        if (!(candidates instanceof Iterable<?> entries)) {
            return possibleSpecies;
        }
        for (Object candidate : entries) {
            if (candidate instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof FormData form
                    && form.getSpecies() != null) {
                possibleSpecies.add(form.getSpecies().getName());
            }
        }
        return possibleSpecies;
    }

    private void addInferredEggSpecies(Map<String, Object> fields, EggMetadata metadata, BlockPos pos) {
        ParentMetadata parents = metadata.parents;
        if (parents.usable && parents.possibleEggSpecies.size() == 1) {
            String species = parents.possibleEggSpecies.iterator().next();
            LOGGER.info("Pasture egg species inferred from Cobbreeding parents at {}: {}", pos, species);
            if (parents.inferredEggSpecies != null) {
                String localizedName = CobblemonText.speciesName(parents.inferredEggSpecies);
                String localizedDisplayName = CobblemonText.displayName(
                        parents.inferredEggSpecies,
                        parents.inferredEggForm
                );
                fields.put("Species", localizedName + " (inferred from parents)");
                fields.put(
                        NotificationService.DISCORD_TITLE,
                        NotificationService.pastureEggDiscordTitle(localizedDisplayName)
                );
                addFormMetadata(fields, parents.inferredEggSpecies, parents.inferredEggForm);
                addPokemonThumbnail(fields, parents.inferredEggSpecies, parents.inferredEggForm);
            } else {
                fields.put("Species", species + " (inferred from parents)");
                addPokemonThumbnail(fields, species);
            }
            return;
        }
        if (parents.usable && !parents.possibleEggSpecies.isEmpty()) {
            fields.put("Possible Egg Species", String.join(", ", parents.possibleEggSpecies));
            return;
        }

        // Not a timing problem: a client is never sent pasture contents at all, so the
        // old "not synchronized" wording sent readers looking for a race that cannot exist.
        String reason = metadata.inventorySynced
                ? "egg metadata unavailable"
                : "pasture contents are not sent to clients";
        LOGGER.warn("Pasture egg detected at {}, but species is unavailable: {}", pos, reason);
        fields.put("Species", "Unavailable (" + reason + ")");
    }

    private static void addPokemonThumbnail(Map<String, Object> fields, String speciesName) {
        try {
            Species species = PokemonSpecies.getByName(speciesName);
            addPokemonThumbnail(fields, species);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not resolve a PokeAPI sprite for egg species {}", speciesName, exception);
        }
    }

    /** Adds a PokeAPI thumbnail from the exact Cobblemon species instance. */
    private static void addPokemonThumbnail(Map<String, Object> fields, Species species) {
        addPokemonThumbnail(fields, species, null);
    }

    /** Uses Cobblemon's texture source when the inferred species has a non-base form. */
    private static void addPokemonThumbnail(Map<String, Object> fields, Species species, FormData form) {
        if (species != null) {
            fields.put(
                    NotificationService.DISCORD_THUMBNAIL_URL,
                    NotificationService.cobblemonSpriteUrl(
                            species.getNationalPokedexNumber(),
                            species.getResourceIdentifier().getPath(),
                            form == null ? List.of() : form.getAspects(),
                            false
                    )
            );
        }
    }

    private static FormData resolveForm(Species species, Set<String> aspects) {
        try {
            return species.getForm(aspects);
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not resolve a cached pasture Pokemon form", exception);
            return species.getStandardForm();
        }
    }

    private static String formatSpeciesDisplay(Species species, Set<String> aspects) {
        FormData form = resolveForm(species, aspects);
        return CobblemonText.displayName(species, form);
    }

    private static boolean sameForm(List<ResolvedCachedParent> parents) {
        return parents.stream()
                .map(parent -> parent.form() == null ? List.<String>of() : parent.form().getAspects())
                .distinct()
                .count() == 1;
    }

    private static void addFormMetadata(Map<String, Object> fields, Species species, FormData form) {
        String formName = CobblemonText.formName(species, form);
        if (!formName.isBlank()) {
            fields.put("Form", formName);
        }
    }

    private static Boolean readHasEgg(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isPastureBlock(state)) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (HAS_EGG_PROPERTY.equals(property.getName())) {
                return Boolean.valueOf(String.valueOf(state.get(property)));
            }
        }
        return null;
    }

    /** Creates the stable in-memory key used for a monitored pasture in this session. */
    private static String pastureKey(String dimension, BlockPos pos) {
        return dimension + ":" + pos.toShortString();
    }

    public static boolean isPastureBlock(ClientWorld world, BlockPos pos) {
        return isPastureBlock(world.getBlockState(pos));
    }

    /** Resolves either half of a two-block pasture to its bottom BlockEntity position. */
    public static BlockPos resolvePastureBase(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isPastureBlock(state)) {
            return null;
        }

        String part = readProperty(state, PART_PROPERTY);
        if (TOP_PART.equalsIgnoreCase(part)) {
            BlockPos bottom = pos.down();
            return isPastureBlock(world.getBlockState(bottom)) ? bottom : null;
        }
        return pos;
    }

    /**
     * Resolves a pasture near a command raycast position when the client reports
     * an air/miss position for the pasture's multi-block model.
     */
    public static BlockPos resolvePastureBaseNearLookTarget(ClientWorld world, BlockPos pos) {
        BlockPos exact = resolvePastureBase(world, pos);
        if (exact != null) {
            return exact;
        }

        for (int offset = -LOOK_TARGET_VERTICAL_FALLBACK; offset <= LOOK_TARGET_VERTICAL_FALLBACK; offset++) {
            if (offset == 0) {
                continue;
            }
            BlockPos candidate = resolvePastureBase(world, pos.add(0, offset, 0));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** Produces safe, local diagnostics for a looked-at pasture block. */
    public List<String> debugAt(ClientWorld world, BlockPos lookedPos) {
        List<String> lines = new ArrayList<>();
        BlockState lookedState = world.getBlockState(lookedPos);
        lines.add("Pasture debug:");
        lines.add("Looked position: " + lookedPos.toShortString());
        lines.add("Looked block: " + Registries.BLOCK.getId(lookedState.getBlock()));
        lines.add("Looked part=" + readProperty(lookedState, PART_PROPERTY)
                + ", has_egg=" + readProperty(lookedState, HAS_EGG_PROPERTY));

        BlockPos base = resolvePastureBaseNearLookTarget(world, lookedPos);
        if (base == null) {
            lines.add("Resolved base: unavailable (look at a Cobblemon pasture block)");
            return lines;
        }

        BlockState baseState = world.getBlockState(base);
        Boolean hasEgg = readHasEgg(world, base);
        EggMetadata metadata = readEggMetadata(world, base, Boolean.TRUE.equals(hasEgg));
        String key = world.getRegistryKey().getValue() + ":" + base.toShortString();
        boolean registered = configManager.getConfig().monitoredPastures.stream().anyMatch(target -> {
            if (!world.getRegistryKey().getValue().toString().equals(target.dimension)) {
                return false;
            }
            BlockPos targetBase = resolvePastureBase(world, new BlockPos(target.x, target.y, target.z));
            return base.equals(targetBase);
        });

        lines.add("Resolved base: " + base.toShortString()
                + (base.equals(lookedPos) ? "" : " (vertical fallback)"));
        lines.add("Base part=" + readProperty(baseState, PART_PROPERTY)
                + ", has_egg=" + hasEgg);
        BlockEntity blockEntity = world.getBlockEntity(base);
        lines.add("BlockEntity: " + (blockEntity == null ? "none" : blockEntity.getClass().getSimpleName()));
        lines.add("Local egg stacks=" + metadata.eggCount
                + " (0 is expected: pasture contents are never sent to clients)"
                + ", species=" + (metadata.species.isEmpty() ? "unavailable" : String.join(", ", metadata.species)));
        lines.add("Pasture parents (cached, " + metadata.parents.species.size() + ")="
                + (metadata.parents.species.isEmpty()
                ? "unavailable"
                : String.join(", ", metadata.parents.species)));
        lines.add("Tethered entries=" + metadata.parents.tetheredEntryCount
                + ", resolved Pokemon=" + metadata.parents.resolvedPokemonCount
                + ", parent data usable=" + metadata.parents.usable);
        lines.add("Possible egg species=" + (metadata.parents.possibleEggSpecies.isEmpty()
                ? "unavailable"
                : String.join(", ", metadata.parents.possibleEggSpecies)));
        lines.add("Monitoring: " + registered + ", observed has_egg=" + observedStates.get(key));
        return lines;
    }

    /**
     * Inspects the persisted pasture registrations instead of relying on the
     * crosshair. This is the authoritative client-side view of monitoring
     * targets and makes unloaded chunks explicit.
     */
    public List<String> debugRegisteredPastures(ClientWorld world) {
        List<String> lines = new ArrayList<>();
        List<ConfigManager.MonitoredPasture> targets = configManager.getConfig().monitoredPastures;
        lines.add("Registered pasture debug: " + targets.size() + " target(s)");
        if (targets.isEmpty()) {
            return lines;
        }

        String currentDimension = world.getRegistryKey().getValue().toString();
        for (ConfigManager.MonitoredPasture target : targets) {
            BlockPos configuredPos = new BlockPos(target.x, target.y, target.z);
            lines.add("Target: " + target.dimension + " " + configuredPos.toShortString());
            lines.add("Registered By: " + (target.registeredByName == null || target.registeredByName.isBlank()
                    ? "unavailable"
                    : target.registeredByName));
            if (!currentDimension.equals(target.dimension)) {
                lines.add("Status: different dimension (current=" + currentDimension + ")");
                continue;
            }
            if (!world.isChunkLoaded(configuredPos)) {
                lines.add("Status: client chunk is not loaded");
                continue;
            }

            BlockState configuredState = world.getBlockState(configuredPos);
            BlockPos base = resolvePastureBase(world, configuredPos);
            if (base == null) {
                lines.add("Configured block: " + Registries.BLOCK.getId(configuredState.getBlock()));
                lines.add("Status: configured position is not a Cobblemon pasture");
                continue;
            }

            Boolean hasEgg = readHasEgg(world, base);
            EggMetadata metadata = readEggMetadata(world, base, Boolean.TRUE.equals(hasEgg));
            String key = target.dimension + ":" + base.toShortString();
            lines.add("Resolved base: " + base.toShortString());
            lines.add("HAS_EGG=" + hasEgg + ", observed=" + observedStates.get(key));
            BlockEntity blockEntity = world.getBlockEntity(base);
            lines.add("BlockEntity: " + (blockEntity == null ? "none" : blockEntity.getClass().getSimpleName()));
            lines.add("Local egg stacks=" + metadata.eggCount
                    + " (0 is expected: pasture contents are never sent to clients)");
            lines.add("Tethered entries=" + metadata.parents.tetheredEntryCount
                    + ", resolved Pokemon=" + metadata.parents.resolvedPokemonCount
                    + ", parent data usable=" + metadata.parents.usable);
            lines.add("Parents (cached, " + metadata.parents.species.size() + ")="
                    + (metadata.parents.species.isEmpty()
                    ? "unavailable"
                    : String.join(", ", metadata.parents.species)));
            lines.add("Possible egg species=" + (metadata.parents.possibleEggSpecies.isEmpty()
                    ? "unavailable"
                    : String.join(", ", metadata.parents.possibleEggSpecies)));
            lines.add("Parent source=" + metadata.parents.source
                    + ", persisted parents=" + target.cachedParents.size());
        }
        // These four are single session-wide values, not per-pasture. Printed after the
        // per-target block they used to read as if they described the last target listed.
        lines.add("--- session-wide, not per-pasture ---");
        lines.add("Last OpenPasturePacket (raw packet, NOT the cache): " + lastOpenPasturePacket);
        lines.add("Last OpenPasture cache result: " + lastOpenPastureCacheStatus);
        lines.add("Last incremental cache update: " + lastPastureCacheUpdate);
        lines.add("Updates dropped this session (no associated GUI): " + ignoredPastureUpdates);
        return lines;
    }

    private static boolean isPastureBlock(BlockState state) {
        return PASTURE_BLOCK_ID.equals(Registries.BLOCK.getId(state.getBlock()).toString())
                && state.getBlock() != Blocks.AIR;
    }

    private static String readProperty(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return String.valueOf(state.get(property));
            }
        }
        return "unavailable";
    }

    private record EggMetadata(
            Set<String> species,
            int eggCount,
            boolean inventorySynced,
            ParentMetadata parents
    ) {
    }

    /**
     * Parent names are a List, not a Set: a pasture may legitimately hold two
     * parents with the same species and form, and collapsing them would report
     * one parent when there are two.
     */
    private record ParentMetadata(
            List<String> species,
            Set<String> possibleEggSpecies,
            int tetheredEntryCount,
            int resolvedPokemonCount,
            boolean usable,
            String source,
            Species inferredEggSpecies,
            FormData inferredEggForm
    ) {
        private static ParentMetadata unavailable() {
            return new ParentMetadata(List.of(), Set.of(), 0, 0, false, "unavailable", null, null);
        }
    }

    /** Preserves the exact server-provided identifier alongside its display name. */
    private record CachedParentSpecies(
            UUID pokemonId,
            Identifier speciesId,
            Set<String> aspects,
            String displayName
    ) {
    }

    /** Couples the display name with the exact species required for its sprite. */
    private record InferredEggSpecies(Set<String> names, Species species, FormData form) {
        private static InferredEggSpecies unavailable() {
            return new InferredEggSpecies(Set.of(), null, null);
        }

        private static InferredEggSpecies of(Species species, FormData form) {
            return species == null
                    ? unavailable()
                    : new InferredEggSpecies(Set.of(CobblemonText.speciesName(species)), species, form);
        }
    }

    /** A GUI parent DTO resolved into the exact form used for Cobbreeding inference. */
    private record ResolvedCachedParent(Species species, FormData form) {
    }
}
