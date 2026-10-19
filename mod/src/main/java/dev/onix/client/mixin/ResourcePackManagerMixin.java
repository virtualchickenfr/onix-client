package dev.onix.client.mixin;

import dev.onix.client.util.ExtraPackFolders;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.path.SymlinkFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Shows resource packs from every pack folder on this PC in the client's pack menu (not in world data packs). */
@Mixin(ResourcePackManager.class)
public abstract class ResourcePackManagerMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet;copyOf([Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;", remap = false))
    private Object[] onix$extraPackFolders(Object[] sources) {
        boolean client = false;
        for (Object s : sources) client |= s instanceof DefaultClientResourcePackProvider;
        if (!client) return sources;
        List<Path> folders = ExtraPackFolders.read();
        if (folders.isEmpty()) return sources;
        // the profile's own folder comes last so its packs win over equally named ones from elsewhere
        List<Object> out = new ArrayList<>();
        int own = -1;
        for (int i = 0; i < sources.length; i++) if (sources[i] instanceof FileResourcePackProvider) own = i;
        for (int i = 0; i < sources.length; i++) {
            if (i == own) {
                for (Path p : folders) out.add(new FileResourcePackProvider(p, ResourceType.CLIENT_RESOURCES, ResourcePackSource.NONE, new SymlinkFinder(path -> false)));
            }
            out.add(sources[i]);
        }
        if (own < 0) {
            for (Path p : folders) out.add(new FileResourcePackProvider(p, ResourceType.CLIENT_RESOURCES, ResourcePackSource.NONE, new SymlinkFinder(path -> false)));
        }
        return out.toArray(new ResourcePackProvider[0]);
    }
}
