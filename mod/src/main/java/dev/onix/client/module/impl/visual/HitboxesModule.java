package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Configurable hitboxes per entity type, with optional look direction. */
public class HitboxesModule extends Module {
    private final NumberSetting range = add(new NumberSetting("range", "Range", 32, 4, 128, 4).unit("blocks"));
    private final BoolSetting self = add(new BoolSetting("self", "Own Hitbox", false));
    private final BoolSetting lookLine = add(new BoolSetting("look", "Look Direction", true));

    {
        group("Entities");
    }

    private final BoolSetting players = add(new BoolSetting("players", "Players", true));
    private final ColorSetting playerColor = add(new ColorSetting("playerColor", "Player Color", 0xFFFFFFFF).visibleIf(players::isOn));
    private final BoolSetting monsters = add(new BoolSetting("monsters", "Monsters", true));
    private final ColorSetting monsterColor = add(new ColorSetting("monsterColor", "Monster Color", 0xFFFF5A5F).visibleIf(monsters::isOn));
    private final BoolSetting animals = add(new BoolSetting("animals", "Other Creatures", true));
    private final ColorSetting animalColor = add(new ColorSetting("animalColor", "Creature Color", 0xFF34D399).visibleIf(animals::isOn));
    private final BoolSetting items = add(new BoolSetting("items", "Dropped Items", false));
    private final ColorSetting itemColor = add(new ColorSetting("itemColor", "Item Color", 0xFFFACC15).visibleIf(items::isOn));
    private final BoolSetting projectiles = add(new BoolSetting("projectiles", "Projectiles", true));
    private final ColorSetting projectileColor = add(new ColorSetting("projectileColor", "Projectile Color", 0xFF38BDF8).visibleIf(projectiles::isOn));

    public HitboxesModule() {
        super("hitboxes", "Hitboxes", "Entity hitboxes with colors per type.", Category.VISUAL, false);
        restricted("Vanilla has hitboxes on F3 + B, but some tournaments do not allow them.");
        tags("f3 b", "bounding box");
    }

    private int colorFor(Entity e) {
        if (e instanceof PlayerEntity) return players.isOn() ? playerColor.color() : 0;
        if (e instanceof Monster) return monsters.isOn() ? monsterColor.color() : 0;
        if (e instanceof LivingEntity) return animals.isOn() ? animalColor.color() : 0;
        if (e instanceof ItemEntity) return items.isOn() ? itemColor.color() : 0;
        if (e instanceof ProjectileEntity) return projectiles.isOn() ? projectileColor.color() : 0;
        return 0;
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || mc().world == null || mc().player == null || context.consumers() == null) return;
            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;
            Vec3d cam = context.camera().getPos();
            float delta = context.tickCounter().getTickDelta(true);
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
            double maxSq = range.get() * range.get();
            for (Entity e : mc().world.getEntities()) {
                if (e == mc().player && (!self.isOn() || mc().options.getPerspective().isFirstPerson())) continue;
                if (e.squaredDistanceTo(mc().player) > maxSq || e.isInvisible()) continue;
                int c = colorFor(e);
                if (c == 0) continue;
                Vec3d lerp = e.getLerpedPos(delta).subtract(e.getPos());
                Box box = e.getBoundingBox().offset(lerp).offset(-cam.x, -cam.y, -cam.z);
                float a = ((c >>> 24) & 0xFF) / 255f, r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
                WorldRenderer.drawBox(matrices, lines, box, r, g, b, a);
                if (lookLine.isOn() && e instanceof LivingEntity) {
                    Vec3d eye = e.getLerpedPos(delta).add(0, e.getStandingEyeHeight(), 0).subtract(cam);
                    Vec3d look = e.getRotationVec(delta).multiply(1.5);
                    Box ray = new Box(eye, eye.add(look)).expand(0.01);
                    WorldRenderer.drawBox(matrices, lines, ray, 0.4f, 0.6f, 1f, a);
                }
            }
        });
    }
}
