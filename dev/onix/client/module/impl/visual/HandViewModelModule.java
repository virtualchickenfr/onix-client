package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.world.InteractionHand;

/** Moves, scales and rotates the items in your hands (first person). Applied by HeldItemRendererMixin. */
public class HandViewModelModule extends Module {
    {
        group("Main Hand");
    }

    private final NumberSetting mainX = add(new NumberSetting("mainX", "Offset X", 0, -1, 1, 0.02));
    private final NumberSetting mainY = add(new NumberSetting("mainY", "Offset Y", 0, -1, 1, 0.02));
    private final NumberSetting mainZ = add(new NumberSetting("mainZ", "Offset Z", 0, -1, 1, 0.02));
    private final NumberSetting mainScale = add(new NumberSetting("mainScale", "Scale", 1, 0.3, 1.5, 0.05).unit("x"));
    private final NumberSetting mainRotation = add(new NumberSetting("mainRot", "Rotation", 0, -90, 90, 1).unit("°"));

    {
        group("Off Hand");
    }

    private final NumberSetting offX = add(new NumberSetting("offX", "Offset X", 0, -1, 1, 0.02));
    private final NumberSetting offY = add(new NumberSetting("offY", "Offset Y", 0, -1, 1, 0.02));
    private final NumberSetting offZ = add(new NumberSetting("offZ", "Offset Z", 0, -1, 1, 0.02));
    private final NumberSetting offScale = add(new NumberSetting("offScale", "Scale", 1, 0.3, 1.5, 0.05).unit("x"));
    private final NumberSetting offRotation = add(new NumberSetting("offRot", "Rotation", 0, -90, 90, 1).unit("°"));

    public HandViewModelModule() {
        super("viewmodel", "Hand View Model", "Position, size and rotation of held items.", Category.VISUAL, false);
        tags("item position", "small items", "low sword");
    }

    /** Applies the transform before vanilla renders the item. Caller pushes/pops the matrix stack. */
    public void apply(PoseStack matrices, InteractionHand hand) {
        boolean main = hand == InteractionHand.MAIN_HAND;
        matrices.translate(main ? mainX.get() : offX.get(), main ? mainY.get() : offY.get(), main ? mainZ.get() : offZ.get());
        float rot = main ? mainRotation.getFloat() : offRotation.getFloat();
        if (rot != 0) matrices.mulPose(Axis.YP.rotationDegrees(main ? rot : -rot));
        float s = main ? mainScale.getFloat() : offScale.getFloat();
        if (s != 1) matrices.scale(s, s, s);
    }
}
