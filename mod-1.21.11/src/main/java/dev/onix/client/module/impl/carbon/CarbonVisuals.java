package dev.onix.client.module.impl.carbon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Carbon's world visuals that only show information: reach circle, landing spot, projectile trajectories,
 * light levels where mobs can spawn, and the spots where players logged out.
 */
public final class CarbonVisuals {
    private CarbonVisuals() {}

    static List<Module> modules() {
        return List.of(new ReachCircle(), new LandingSpot(), new Trajectories(), new LightLevels(), new LogoutSpots(), new LogoutList());
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static int argb(int rgb, float a) {
        return FxDraw.withAlpha(rgb, a);
    }

    // ================================================================== reach circle

    static final class ReachCircle extends Module {
        private final ColorSetting color = add(ColorSetting.accent("color", "Color"));
        private final NumberSetting radius = add(new NumberSetting("radius", "Radius", 3, 2, 6, 0.5).unit("blocks"));
        private final BoolSetting others = add(new BoolSetting("others", "Around Other Players", false).describe("Shows how far other players can hit you."));

        ReachCircle() {
            super("cb_reachcircle", "Reach Circle", "A ring on the ground showing your attack reach.", Category.VISUAL, false);
            tags("reach", "circle", "pvp", "range");
        }

        @Override
        public void init() {
            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                if (!isEnabled() || mc().player == null || context.consumers() == null || context.matrices() == null) return;
                PoseStack m = context.matrices();
                Vec3 cam = WorldDraw.camera(context);
                float d = WorldDraw.partialTick();
                VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugQuads());
                float r = radius.getFloat();
                for (Player p : others.isOn() ? mc().level.players() : List.of((Player) mc().player)) {
                    if (p != mc().player && p.distanceTo(mc().player) > 32) continue;
                    Vec3 pos = p.getPosition(d).subtract(cam);
                    int c = color.color();
                    FxDraw.ringUp(m, vc, pos.x, pos.y + 0.02, pos.z, r - 0.05f, r + 0.05f, argb(c, 0.85f), argb(c, 0.85f), 72);
                    FxDraw.ringUp(m, vc, pos.x, pos.y + 0.02, pos.z, r - 0.4f, r - 0.05f, argb(c, 0f), argb(c, 0.25f), 72);
                }
            });
        }
    }

    // ================================================================== landing spot

    static final class LandingSpot extends Module {
        private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFF5A5A));

        LandingSpot() {
            super("cb_landingspot", "Landing Spot", "Marks the block you will land on while you fall or fly.", Category.VISUAL, false);
            tags("landing", "fall", "mace", "elytra");
        }

        @Override
        public void init() {
            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                LocalPlayer p = mc().player;
                if (!isEnabled() || p == null || p.onGround() || p.isInWater() || context.consumers() == null || context.matrices() == null) return;
                float d = WorldDraw.partialTick();
                Vec3 from = p.getPosition(d);
                Vec3 v = p.getDeltaMovement();
                // follow the current motion for a few seconds, falling with gravity
                Vec3 pos = from;
                Vec3 hit = null;
                for (int i = 0; i < 100 && hit == null; i++) {
                    v = p.isFallFlying() ? v : new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91);
                    Vec3 next = pos.add(v);
                    BlockHitResult r = mc().level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
                    if (r.getType() != HitResult.Type.MISS) hit = r.getLocation();
                    pos = next;
                }
                if (hit == null) return;
                Vec3 cam = WorldDraw.camera(context);
                float dist = (float) (from.y - hit.y);
                if (dist < 1.5f) return;
                int c = color.color();
                float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugQuads());
                FxDraw.ringUp(context.matrices(), vc, hit.x - cam.x, hit.y - cam.y + 0.02, hit.z - cam.z, 0f, 0.45f, argb(c, 0.15f), argb(c, 0.55f * pulse), 40);
                FxDraw.ringUp(context.matrices(), vc, hit.x - cam.x, hit.y - cam.y + 0.021, hit.z - cam.z, 0.45f, 0.55f, argb(c, 0.9f), argb(c, 0.9f), 40);
            });
        }
    }

    // ================================================================== trajectories

    static final class Trajectories extends Module {
        private final ColorSetting color = add(ColorSetting.accent("color", "Color"));

        Trajectories() {
            super("cb_trajectories", "Trajectories", "Shows where your pearl, bow shot, trident, potion or snowball will fly and land.", Category.VISUAL, false);
            tags("trajectory", "pearl", "bow", "aim", "projectile");
        }

        /** speed, gravity, drag, pitch offset; null if the item is not thrown */
        private static float[] physics(LocalPlayer p, ItemStack s) {
            if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG)) return new float[]{1.5f, 0.03f, 0.99f, 0};
            if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new float[]{0.5f, 0.05f, 0.99f, -20};
            if (s.is(Items.EXPERIENCE_BOTTLE)) return new float[]{0.7f, 0.07f, 0.99f, -20};
            if (s.is(Items.WIND_CHARGE)) return new float[]{1.5f, 0f, 1f, 0};
            if (s.is(Items.TRIDENT) && p.isUsingItem()) return new float[]{2.5f, 0.05f, 0.99f, 0};
            if (s.is(Items.BOW) && p.isUsingItem()) {
                float power = BowItem.getPowerForTime(p.getTicksUsingItem());
                return power < 0.1f ? null : new float[]{power * 3f, 0.05f, 0.99f, 0};
            }
            if (s.is(Items.CROSSBOW)) return new float[]{3.15f, 0.05f, 0.99f, 0};
            return null;
        }

        @Override
        public void init() {
            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                LocalPlayer p = mc().player;
                if (!isEnabled() || p == null || context.consumers() == null || context.matrices() == null) return;
                ItemStack s = p.getMainHandItem();
                float[] ph = physics(p, s);
                if (ph == null) {
                    s = p.getOffhandItem();
                    ph = physics(p, s);
                }
                if (ph == null) return;
                float d = WorldDraw.partialTick();
                float yaw = p.getViewYRot(d), pitch = p.getViewXRot(d);
                double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch + ph[3]), rp0 = Math.toRadians(pitch);
                Vec3 dir = new Vec3(-Math.sin(ry) * Math.cos(rp), -Math.sin(Math.toRadians(pitch + ph[3])), Math.cos(ry) * Math.cos(rp)).normalize();
                if (ph[3] == 0) dir = new Vec3(-Math.sin(ry) * Math.cos(rp0), -Math.sin(rp0), Math.cos(ry) * Math.cos(rp0)).normalize();
                Vec3 pos = p.getEyePosition(d).subtract(0, 0.1, 0);
                Vec3 v = dir.scale(ph[0]).add(p.getDeltaMovement().x, p.onGround() ? 0 : p.getDeltaMovement().y, p.getDeltaMovement().z);
                List<Vec3> points = new ArrayList<>();
                points.add(pos);
                Vec3 hit = null;
                for (int i = 0; i < 300 && hit == null; i++) {
                    Vec3 next = pos.add(v);
                    BlockHitResult r = mc().level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                    if (r.getType() != HitResult.Type.MISS) {
                        hit = r.getLocation();
                        next = hit;
                    }
                    // other players in the way
                    AABB path = new AABB(pos, next).inflate(0.3);
                    for (Player o : mc().level.players()) {
                        if (o != p && o.getBoundingBox().intersects(path)) {
                            hit = o.getBoundingBox().getCenter();
                            next = hit;
                            break;
                        }
                    }
                    points.add(next);
                    pos = next;
                    v = v.scale(ph[2]).subtract(0, ph[1], 0);
                    if (pos.y < mc().level.getMinY() - 16) break;
                }
                Vec3 cam = WorldDraw.camera(context);
                int c = color.color();
                VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugQuads());
                Matrix4f mat = context.matrices().last().pose();
                for (int i = 1; i < points.size(); i++) {
                    float a = Math.min(1f, i / 6f) * 0.9f;
                    ribbon(vc, mat, points.get(i - 1).subtract(cam), points.get(i).subtract(cam), 0.025f, argb(c, a));
                }
                if (hit != null) {
                    Vec3 h = hit.subtract(cam);
                    WorldDraw.filledBox(context.matrices(), vc, h.x - 0.2, h.y - 0.2, h.z - 0.2, h.x + 0.2, h.y + 0.2, h.z + 0.2,
                            ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 0.35f);
                    WorldDraw.lineBox(context.matrices(), context.consumers().getBuffer(RenderTypes.lines()),
                            new AABB(h.x - 0.2, h.y - 0.2, h.z - 0.2, h.x + 0.2, h.y + 0.2, h.z + 0.2),
                            ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
                }
            });
        }

        /** A thin cross-shaped ribbon between two points (visible from every side). */
        private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float w, int color) {
            float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
            quad(vc, m, color, ax, ay - w, az, bx, by - w, bz, bx, by + w, bz, ax, ay + w, az);
            quad(vc, m, color, ax, ay + w, az, bx, by + w, bz, bx, by - w, bz, ax, ay - w, az);
            quad(vc, m, color, ax - w, ay, az, bx - w, by, bz, bx + w, by, bz, ax + w, ay, az);
            quad(vc, m, color, ax + w, ay, az, bx + w, by, bz, bx - w, by, bz, ax - w, ay, az);
            quad(vc, m, color, ax, ay, az - w, bx, by, bz - w, bx, by, bz + w, ax, ay, az + w);
            quad(vc, m, color, ax, ay, az + w, bx, by, bz + w, bx, by, bz - w, ax, ay, az - w);
        }

        private static void quad(VertexConsumer vc, Matrix4f m, int c, float... p) {
            for (int i = 0; i < 12; i += 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(c);
        }
    }

    // ================================================================== light levels

    static final class LightLevels extends Module {
        private final NumberSetting range = add(new NumberSetting("range", "Range", 12, 4, 24, 1).unit("blocks"));
        private final BoolSetting onlyDark = add(new BoolSetting("dark", "Only Spawnable Spots", true).describe("Only marks blocks where hostile mobs can spawn (block light 0)."));
        private final List<long[]> spots = new ArrayList<>(); // x, y, z, light
        private int timer;

        LightLevels() {
            super("cb_lightlevels", "Light Levels", "Marks the ground where mobs can spawn so you know where to put torches.", Category.VISUAL, false);
            tags("light", "torch", "spawn", "mobs");
        }

        @Override
        public void onTick() {
            if (mc().level == null || mc().player == null || --timer > 0) return;
            timer = 10;
            spots.clear();
            BlockPos c = mc().player.blockPosition();
            int r = range.getInt();
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    for (int y = -4; y <= 3; y++) {
                        q.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                        BlockState here = mc().level.getBlockState(q);
                        if (!here.isAir()) continue;
                        BlockState below = mc().level.getBlockState(q.below());
                        if (!below.isFaceSturdy(mc().level, q.below(), net.minecraft.core.Direction.UP)) continue;
                        int light = mc().level.getBrightness(LightLayer.BLOCK, q);
                        if (onlyDark.isOn() && light > 0) continue;
                        spots.add(new long[]{q.getX(), q.getY(), q.getZ(), light});
                        if (spots.size() > 4000) return;
                    }
                }
            }
        }

        @Override
        protected void onDisable() {
            spots.clear();
        }

        @Override
        public void init() {
            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                if (!isEnabled() || spots.isEmpty() || context.consumers() == null || context.matrices() == null) return;
                Vec3 cam = WorldDraw.camera(context);
                VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugQuads());
                Matrix4f m = context.matrices().last().pose();
                for (long[] s : spots) {
                    float x = (float) (s[0] - cam.x), y = (float) (s[1] - cam.y + 0.01), z = (float) (s[2] - cam.z);
                    int c = s[3] == 0 ? 0x66FF3B3B : s[3] < 8 ? 0x55FFC23D : 0x4434D399;
                    vc.addVertex(m, x + 0.1f, y, z + 0.1f).setColor(c);
                    vc.addVertex(m, x + 0.1f, y, z + 0.9f).setColor(c);
                    vc.addVertex(m, x + 0.9f, y, z + 0.9f).setColor(c);
                    vc.addVertex(m, x + 0.9f, y, z + 0.1f).setColor(c);
                }
            });
        }
    }

    // ================================================================== logout spots

    record Logout(String name, AABB box, long time) {}

    static final Map<UUID, Logout> LOGOUTS = new LinkedHashMap<>();
    private static final Map<UUID, Player> LAST_SEEN = new HashMap<>();

    static void trackLogouts() {
        var conn = mc().getConnection();
        if (mc().level == null || conn == null) {
            LOGOUTS.clear();
            LAST_SEEN.clear();
            return;
        }
        Map<UUID, Player> now = new HashMap<>();
        for (Player p : mc().level.players()) if (p != mc().player) now.put(p.getUUID(), p);
        for (var e : LAST_SEEN.entrySet()) {
            if (!now.containsKey(e.getKey()) && conn.getPlayerInfo(e.getKey()) == null) {
                Player p = e.getValue();
                LOGOUTS.put(e.getKey(), new Logout(p.getName().getString(), p.getBoundingBox(), System.currentTimeMillis()));
            }
        }
        for (UUID id : now.keySet()) LOGOUTS.remove(id);
        LAST_SEEN.clear();
        LAST_SEEN.putAll(now);
        while (LOGOUTS.size() > 20) LOGOUTS.remove(LOGOUTS.keySet().iterator().next());
    }

    static final class LogoutSpots extends Module {
        private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFC23D));

        LogoutSpots() {
            super("cb_logoutspots", "Logout Spots", "Marks where players logged out with a box (their name is in the Logout List).", Category.VISUAL, false);
            tags("logout", "combat log", "players");
        }

        @Override
        public void onTick() {
            trackLogouts();
        }

        @Override
        public void init() {
            WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                if (!isEnabled() || LOGOUTS.isEmpty() || context.consumers() == null || context.matrices() == null) return;
                Vec3 cam = WorldDraw.camera(context);
                int c = color.color();
                float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
                VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
                for (Logout l : LOGOUTS.values()) WorldDraw.lineBox(context.matrices(), lines, l.box().move(-cam.x, -cam.y, -cam.z), r, g, b, 1f);
                VertexConsumer quads = context.consumers().getBuffer(RenderTypes.debugQuads());
                for (Logout l : LOGOUTS.values()) {
                    AABB bx = l.box().move(-cam.x, -cam.y, -cam.z);
                    WorldDraw.filledBox(context.matrices(), quads, bx.minX, bx.minY, bx.minZ, bx.maxX, bx.maxY, bx.maxZ, r, g, b, 0.15f);
                }
            });
        }
    }

    static final class LogoutList extends TextHudModule {
        LogoutList() {
            super("cb_logoutlist", "Logout List", "Lists who logged out near you, where and how long ago.", Category.HUD, false, Anchor.TOP_RIGHT, 4, 120);
            tags("logout", "combat log", "players");
        }

        @Override
        public void onTick() {
            trackLogouts();
        }

        @Override
        protected void lines(List<Line> out, boolean preview) {
            if (LOGOUTS.isEmpty()) {
                if (preview) out.add(new Line("Steve", "120 64 -30 · 12m · 0:42", 0xFFFFC23D));
                return;
            }
            Vec3 me = mc().player == null ? Vec3.ZERO : mc().player.position();
            for (Logout l : LOGOUTS.values()) {
                Vec3 c = l.box().getBottomCenter();
                long s = (System.currentTimeMillis() - l.time()) / 1000;
                out.add(new Line(l.name(), String.format(Locale.ROOT, "%d %d %d · %dm · %d:%02d", (int) c.x, (int) c.y, (int) c.z,
                        Math.round(c.distanceTo(me)), s / 60, s % 60), 0xFFFFC23D));
            }
        }
    }
}
