package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BlockInfoModule extends TextHudModule {
    private final BoolSetting position = add(new BoolSetting("position", "Position", true));
    private final BoolSetting id = add(new BoolSetting("id", "Block ID", false));
    private final BoolSetting hardness = add(new BoolSetting("hardness", "Hardness", false));
    private final BoolSetting distance = add(new BoolSetting("distance", "Distance", false));

    public BlockInfoModule() {
        super("blockinfo", "Block Info", "Details about the block you are looking at.", Category.HUD, false, Anchor.TOP_CENTER, 0, 66);
        tags("looking at", "target block", "waila");
    }

    @Override
    public boolean hasContent() {
        return mc().hitResult != null && mc().hitResult.getType() == HitResult.Type.BLOCK;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (!(mc().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc().level == null) {
            if (preview) out.add(new Line("Block", "Stone"));
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc().level.getBlockState(pos);
        out.add(new Line("Block", state.getBlock().getName().getString()));
        if (position.isOn()) out.add(new Line("At", pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        if (id.isOn()) out.add(new Line("ID", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        if (hardness.isOn()) out.add(new Line("Hardness", String.format(Locale.ROOT, "%.1f", state.getDestroySpeed(mc().level, pos))));
        if (distance.isOn() && mc().player != null) {
            out.add(new Line("Distance", String.format(Locale.ROOT, "%.1f", Math.sqrt(mc().player.getEyePosition().distanceToSqr(hit.getLocation())))));
        }
    }
}
