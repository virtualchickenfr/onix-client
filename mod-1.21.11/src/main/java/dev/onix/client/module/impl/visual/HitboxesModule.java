package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import dev.onix.client.render.WorldDraw;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        if (e instanceof Player) return players.isOn() ? playerColor.color() : 0;
        if (e instanceof Enemy) return monsters.isOn() ? monsterColor.color() : 0;
        if (e instanceof LivingEntity) return animals.isOn() ? animalColor.color() : 0;
        if (e instanceof ItemEntity) return items.isOn() ? itemColor.color() : 0;
        if (e instanceof Projectile) return projectiles.isOn() ? projectileColor.color() : 0;
        return 0;
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || mc().level == null || mc().player == null || context.consumers() == null) return;
            PoseStack matrices = context.matrices();
            if (matrices == null) return;
            Vec3 cam = WorldDraw.camera(context);
            float delta = WorldDraw.partialTick();
            VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
            double maxSq = range.get() * range.get();
            for (Entity e : mc().level.entitiesForRendering()) {
                if (e == mc().player && (!self.isOn() || mc().options.getCameraType().isFirstPerson())) continue;
                if (e.distanceToSqr(mc().player) > maxSq || e.isInvisible()) continue;
                int c = colorFor(e);
                if (c == 0) continue;
                Vec3 lerp = e.getPosition(delta).subtract(e.position());
                AABB box = e.getBoundingBox().move(lerp).move(-cam.x, -cam.y, -cam.z);
                float a = ((c >>> 24) & 0xFF) / 255f, r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
                WorldDraw.lineBox(matrices, lines, box, r, g, b, a);
                if (lookLine.isOn() && e instanceof LivingEntity) {
                    Vec3 eye = e.getPosition(delta).add(0, e.getEyeHeight(), 0).subtract(cam);
                    Vec3 look = e.getViewVector(delta).scale(1.5);
                    AABB ray = new AABB(eye, eye.add(look)).inflate(0.01);
                    WorldDraw.lineBox(matrices, lines, ray, 0.4f, 0.6f, 1f, a);
                }
            }
        });
    }
}
