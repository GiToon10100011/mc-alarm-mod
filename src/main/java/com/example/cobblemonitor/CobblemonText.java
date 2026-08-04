package com.example.cobblemonitor;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.text.Text;

import java.util.Locale;

/** Resolves Cobblemon species and form labels through the current game language. */
final class CobblemonText {
    private static final String FORM_TRANSLATION_PREFIX = "cobblemon.ui.pokedex.info.form.";

    private CobblemonText() {
    }

    static String speciesName(Species species) {
        if (species == null) {
            return "Unknown";
        }
        String translated = species.getTranslatedName().getString();
        return translated == null || translated.isBlank() ? species.getName() : translated;
    }

    static String formName(Species species, FormData form) {
        if (species == null || form == null || form.getAspects().isEmpty()
                || form.getName() == null || form.getName().isBlank()) {
            return "";
        }
        String rawName = form.getName();
        String formKeyPart = rawName.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        String key = FORM_TRANSLATION_PREFIX + species.getResourceIdentifier().getPath() + "-" + formKeyPart;
        String translated = Text.translatable(key).getString();
        return key.equals(translated) ? rawName : translated;
    }

    static String displayName(Species species, FormData form) {
        String formName = formName(species, form);
        return formName.isBlank() ? speciesName(species) : speciesName(species) + " (" + formName + ")";
    }
}
