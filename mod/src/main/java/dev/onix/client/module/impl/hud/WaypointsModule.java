package dev.onix.client.module.impl.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Simple waypoints: save your position with a key, see distance and direction on the HUD. */
public class WaypointsModule extends TextHudModule {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private record Waypoint(String name, int x, int y, int z, String dimension) {}

    private final KeybindSetting addKey = add(new KeybindSetting("addKey", "Add Waypoint Key", KeybindSetting.NONE));
    private final KeybindSetting removeKey = add(new KeybindSetting("removeKey", "Remove Nearest Key", KeybindSetting.NONE));
    private final TextSetting namePrefix = add(new TextSetting("prefix", "Name Prefix", "Waypoint", 16));
    private final NumberSetting maxShown = add(new NumberSetting("max", "Max Shown", 5, 1, 20, 1));
    private final BoolSetting arrows = add(new BoolSetting("arrows", "Direction Arrows", true));
    private final BoolSetting thisDimension = add(new BoolSetting("thisDim", "Only This Dimension", true));
    private final TextSetting data = add(new TextSetting("data", "Data", "[]", 100_000).hidden());

    private final List<Waypoint> waypoints = new ArrayList<>();
    private String loaded = "";

    public WaypointsModule() {
        super("waypoints", "Waypoints", "Mark places with a key and see how far away they are.", Category.HUD, false, Anchor.MIDDLE_LEFT, 4, 0);
        tags("markers", "home", "base", "navigation");
    }

    private String dimension() {
        return mc().world == null ? "overworld" : mc().world.getRegistryKey().getValue().getPath();
    }

    private void load() {
        if (data.get().equals(loaded)) return;
        loaded = data.get();
        waypoints.clear();
        try {
            for (JsonElement e : JsonParser.parseString(loaded).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                waypoints.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                        o.get("z").getAsInt(), o.get("dim").getAsString()));
            }
        } catch (Exception ignored) {
            // broken data: start empty
        }
    }

    private void save() {
        JsonArray array = new JsonArray();
        for (Waypoint w : waypoints) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("x", w.x());
            o.addProperty("y", w.y());
            o.addProperty("z", w.z());
            o.addProperty("dim", w.dimension());
            array.add(o);
        }
        loaded = array.toString();
        data.set(loaded);
    }

    private double distance(Waypoint w) {
        if (mc().player == null) return 0;
        double dx = w.x() + 0.5 - mc().player.getX();
        double dz = w.z() + 0.5 - mc().player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public void onTick() {
        load();
        if (mc().player == null) return;
        if (addKey.poll()) {
            var p = mc().player.getBlockPos();
            String name = namePrefix.get() + " " + (waypoints.size() + 1);
            waypoints.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), dimension()));
            save();
            Notifications.show("Waypoint added", name + " at " + p.getX() + ", " + p.getY() + ", " + p.getZ());
        }
        if (removeKey.poll() && !waypoints.isEmpty()) {
            Waypoint nearest = waypoints.stream().filter(w -> w.dimension().equals(dimension()))
                    .min(Comparator.comparingDouble(this::distance)).orElse(null);
            if (nearest != null) {
                waypoints.remove(nearest);
                save();
                Notifications.show("Waypoint removed", nearest.name());
            }
        }
    }

    private String arrow(Waypoint w) {
        if (mc().player == null) return "";
        double dx = w.x() + 0.5 - mc().player.getX();
        double dz = w.z() + 0.5 - mc().player.getZ();
        float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float rel = MathHelper.wrapDegrees(target - mc().player.getYaw());
        return ARROWS[Math.floorMod(Math.round(rel / 45f), 8)] + " ";
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        load();
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint w : waypoints) if (!thisDimension.isOn() || w.dimension().equals(dimension())) list.add(w);
        list.sort(Comparator.comparingDouble(this::distance));
        if (list.isEmpty()) {
            if (preview) out.add(new Line("Home", "↑ 128 m"));
            return;
        }
        for (int i = 0; i < Math.min(maxShown.getInt(), list.size()); i++) {
            Waypoint w = list.get(i);
            out.add(new Line(w.name(), (arrows.isOn() ? arrow(w) : "") + Math.round(distance(w)) + " m"));
        }
    }
}
