package dev.onix.client.gui;

/**
 * A color theme. Built-in themes ship with the client; custom themes are created by the
 * player and stored as JSON in config/onix/themes (the launcher reads and writes the same files).
 *
 * @param id      stable identifier ("onyx", or "custom-1a2b3c" for custom themes)
 * @param ambient particles in menus and, with the Atmosphere module, in the world
 */
public record ThemeDef(String id, String name, int accent, int panel, int sky, Ambient ambient, int particle, boolean custom) {
    public enum Ambient {
        SPARKS("Sparks"),
        PETALS("Petals"),
        SNOW("Snow"),
        EMBERS("Embers");

        public final String displayName;

        Ambient(String displayName) {
            this.displayName = displayName;
        }

        public static Ambient byName(String name) {
            for (Ambient a : values()) if (a.displayName.equalsIgnoreCase(name) || a.name().equalsIgnoreCase(name)) return a;
            return SPARKS;
        }

        public static String[] names() {
            Ambient[] all = values();
            String[] out = new String[all.length];
            for (int i = 0; i < all.length; i++) out[i] = all[i].displayName;
            return out;
        }
    }

    private static ThemeDef builtIn(String name, int accent, int panel, int sky, Ambient ambient, int particle) {
        return new ThemeDef(name.toLowerCase(java.util.Locale.ROOT), name, accent, panel, sky, ambient, particle, false);
    }

    public static final ThemeDef[] BUILT_IN = {
            builtIn("Onyx", 0xFFECECF1, 0xFF0D0D11, 0xFF6C63FF, Ambient.SPARKS, 0xFFFFFFFF),
            builtIn("Aurora", 0xFF8B7CFF, 0xFF0E0C17, 0xFF7C6CF6, Ambient.SPARKS, 0xFFC4BAFF),
            builtIn("Cobalt", 0xFF4C8DFF, 0xFF0A0E17, 0xFF4F7BE8, Ambient.SPARKS, 0xFF9DB9FF),
            builtIn("Glacier", 0xFF5EEAD4, 0xFF0A1312, 0xFF9EE6E0, Ambient.SNOW, 0xFFFFFFFF),
            builtIn("Sakura", 0xFFFF8FC4, 0xFF140D12, 0xFFD88BD8, Ambient.PETALS, 0xFFFFB7D5),
            builtIn("Ember", 0xFFFFB547, 0xFF130E09, 0xFFFF9A3C, Ambient.EMBERS, 0xFFFFC27A),
            builtIn("Crimson", 0xFFFF4D5E, 0xFF130A0C, 0xFFE0404F, Ambient.EMBERS, 0xFFFF7A86),
    };

    public ThemeDef withName(String value) {
        return new ThemeDef(id, value, accent, panel, sky, ambient, particle, custom);
    }

    public ThemeDef withColors(int accent, int panel, int sky, int particle) {
        return new ThemeDef(id, name, accent, panel, sky, ambient, particle, custom);
    }

    public ThemeDef withAmbient(Ambient value) {
        return new ThemeDef(id, name, accent, panel, sky, value, particle, custom);
    }

    /** A custom copy with a new id and name. */
    public ThemeDef copyAs(String newId, String newName) {
        return new ThemeDef(newId, newName, accent, panel, sky, ambient, particle, true);
    }
}
