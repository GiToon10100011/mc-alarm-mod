package com.example.cobblemonitor;

import net.minecraft.item.ItemStack;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a Cobbreeding egg's own description off its ItemStack.
 *
 * <p>Cobbreeding stores the egg's Pokemon as a {@code cobbreeding:pokemon_properties}
 * data component holding Cobblemon's plain {@code key=value} property string, and data
 * components travel with the stack, so everything below is available to a client with no
 * server support. The component type is looked up by name rather than through
 * Cobbreeding's own class, keeping this free of a compile dependency exactly like the
 * blockstate properties {@code PastureEggNotifier} reads.
 *
 * <p>A server may set {@code eggEncryptionEnabled}, which replaces the string with AES
 * ciphertext keyed by a file only the server holds. That is indistinguishable from an
 * unreadable string here and simply yields no result.
 */
public final class EggInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.LOGGER_NAME);
    private static final Identifier PROPERTIES_COMPONENT_ID =
            Identifier.of("cobbreeding", "pokemon_properties");
    private static final String[] IV_KEYS = {
            "HP_iv", "ATTACK_iv", "DEFENCE_iv", "SPECIAL_ATTACK_iv", "SPECIAL_DEFENCE_iv", "SPEED_iv"
    };

    private EggInspector() {
    }

    /** One egg as its own properties string described it, or null for anything else. */
    public record EggData(String species, boolean shiny, int[] ivs, double averageIv) {
        public boolean isNotable(ConfigManager.EggHighlight settings) {
            if (shiny && settings.highlightShiny) {
                return true;
            }
            return averageIv >= settings.minAverageIv;
        }
    }

    /** Returns null when the stack is not an egg, or its properties cannot be read. */
    public static EggData read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String properties = readProperties(stack);
        if (properties == null || properties.isBlank()) {
            return null;
        }
        int[] ivs = new int[IV_KEYS.length];
        int found = 0;
        for (int index = 0; index < IV_KEYS.length; index++) {
            Integer value = readInt(properties, IV_KEYS[index]);
            if (value != null) {
                ivs[index] = value;
                found++;
            }
        }
        // A partial IV set would skew the average, so treat it as unreadable.
        if (found != IV_KEYS.length) {
            LOGGER.debug("Ignoring an egg whose properties list {} of {} IVs", found, IV_KEYS.length);
            return null;
        }
        int total = 0;
        for (int iv : ivs) {
            total += iv;
        }
        return new EggData(
                readSpecies(properties),
                "true".equals(readValue(properties, "shiny")),
                ivs,
                (double) total / IV_KEYS.length
        );
    }

    private static String readProperties(ItemStack stack) {
        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(PROPERTIES_COMPONENT_ID);
        if (type == null) {
            return null;
        }
        Object value = stack.getComponents().get(type);
        return value instanceof String text ? text : null;
    }

    /** Cobblemon writes the species first, before any {@code key=value} pair. */
    private static String readSpecies(String properties) {
        String head = properties.strip();
        int space = head.indexOf(' ');
        if (space > 0) {
            head = head.substring(0, space);
        }
        return head.contains("=") ? "" : head;
    }

    private static Integer readInt(String properties, String key) {
        String value = readValue(properties, key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Matches a whole key so {@code ATTACK_iv} is never read out of
     * {@code SPECIAL_ATTACK_iv}, which shares its ending.
     */
    private static String readValue(String properties, String key) {
        int from = 0;
        while (true) {
            int at = properties.indexOf(key + "=", from);
            if (at < 0) {
                return null;
            }
            if (at == 0 || properties.charAt(at - 1) == ' ') {
                int start = at + key.length() + 1;
                int end = properties.indexOf(' ', start);
                return end < 0 ? properties.substring(start) : properties.substring(start, end);
            }
            from = at + 1;
        }
    }
}
