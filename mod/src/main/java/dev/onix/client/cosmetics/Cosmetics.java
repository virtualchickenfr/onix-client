package dev.onix.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import dev.onix.client.config.ConfigFiles;
import dev.onix.client.network.OnixWallet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The current cosmetic selection, one item per slot. Stored in config/onix/cosmetics.json
 * (the launcher writes to it as well). The items themselves come from {@link CosmeticCatalog}.
 * Cosmetics are only visible to yourself.
 */
public final class Cosmetics {
    public enum Slot {
        CAPE("cape", "Cape"),
        HEAD("head", "Head"),
        FACE("face", "Face"),
        NECK("neck", "Neck"),
        BACK("back", "Back"),
        AURA("aura", "Aura"),
        PET("pet", "Pet");

        public final String key;
        public final String displayName;

        Slot(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public static Slot byKey(String key) {
            for (Slot s : values()) if (s.key.equals(key)) return s;
            return null;
        }
    }

    public static final Identifier WHITE = Identifier.of("onix", "textures/cosmetics/white.png");

    private static final Map<Slot, String> SELECTED = new EnumMap<>(Slot.class);
    /** Items tried on while hovering in the wardrobe; shown instead of the selection, never saved. */
    private static final Map<Slot, String> PREVIEW = new EnumMap<>(Slot.class);

    private static Boolean cosmetica;

    private Cosmetics() {}

    /** True if the Cosmetica mod (free cosmetics others with Cosmetica can see) is installed next to Onix. */
    public static boolean cosmeticaInstalled() {
        if (cosmetica == null) {
            cosmetica = FabricLoader.getInstance().getAllMods().stream()
                    .anyMatch(m -> m.getMetadata().getId().startsWith("cosmetica"));
        }
        return cosmetica;
    }

    /** Onix steps back with its cape so the two mods do not fight over it (a cape being tried on still shows). */
    public static boolean capeLeftToCosmetica() {
        return cosmeticaInstalled() && OnixClient.CONFIG != null && OnixClient.CONFIG.cosmeticaCape.isOn() && !PREVIEW.containsKey(Slot.CAPE);
    }

    private static Path file() {
        return ConfigFiles.dir().resolve("cosmetics.json");
    }

    public static List<CosmeticCatalog.Item> of(Slot slot) {
        return CosmeticCatalog.items(slot);
    }

    public static String selected(Slot slot) {
        return SELECTED.get(slot);
    }

    public static boolean isSelected(CosmeticCatalog.Item item) {
        return item.id().equals(SELECTED.get(item.slot()));
    }

    /** What is drawn in a slot right now: the tried-on item, or the selection if you own it. */
    public static CosmeticCatalog.Item shown(Slot slot) {
        if (PREVIEW.containsKey(slot)) return CosmeticCatalog.item(PREVIEW.get(slot));
        CosmeticCatalog.Item item = CosmeticCatalog.item(SELECTED.get(slot));
        return OnixWallet.owns(item) ? item : null;
    }

    /** Passing null as id removes the cosmetic from that slot. */
    public static void select(Slot slot, String id) {
        CosmeticCatalog.Item item = CosmeticCatalog.item(id);
        if (item == null || item.slot() != slot) SELECTED.remove(slot);
        else SELECTED.put(slot, item.id());
        save();
    }

    /** Tries an item on (null id = show the slot empty). */
    public static void preview(Slot slot, String id) {
        PREVIEW.put(slot, id);
    }

    public static void clearPreview() {
        PREVIEW.clear();
    }

    public static int equippedCount() {
        int n = 0;
        for (String id : SELECTED.values()) if (OnixWallet.owns(CosmeticCatalog.item(id))) n++;
        return n;
    }

    public static Identifier capeTexture() {
        if (capeLeftToCosmetica()) return null;
        CosmeticCatalog.Item item = shown(Slot.CAPE);
        return item == null ? null : CapeTextures.get(item);
    }

    /** Cosmetics are only drawn on the local player. */
    public static boolean appliesTo(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && player.getUuid().equals(mc.player.getUuid()) && !player.isInvisible();
    }

    /**
     * Reads the selection. Older files used the keys "cape", "hat" and "extra" with the ids of the first
     * cosmetics; those ids are aliases in the catalog, so each one lands in the slot of the item it maps to.
     */
    public static void load() {
        SELECTED.clear();
        PREVIEW.clear();
        JsonObject json = ConfigFiles.read(file());
        if (json == null) return;
        boolean migrated = false;
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            CosmeticCatalog.Item item = CosmeticCatalog.item(e.getValue().getAsString());
            if (item == null) continue;
            Slot keySlot = Slot.byKey(e.getKey());
            if (keySlot != null && keySlot != item.slot()) continue; // an item saved under the wrong slot
            if (keySlot == null) migrated = true;                    // "hat" / "extra"
            if (keySlot != null || !SELECTED.containsKey(item.slot())) SELECTED.put(item.slot(), item.id());
            if (!item.id().equals(e.getValue().getAsString())) migrated = true;
        }
        if (migrated) save();
    }

    public static void save() {
        JsonObject json = new JsonObject();
        for (Slot slot : Slot.values()) {
            String id = SELECTED.get(slot);
            if (id != null) json.addProperty(slot.key, id);
        }
        ConfigFiles.write(file(), json);
    }
}
