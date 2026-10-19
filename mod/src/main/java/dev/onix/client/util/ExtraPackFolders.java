package dev.onix.client.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Resource pack folders from elsewhere on this PC (other Onix profiles, the vanilla launcher and other launchers).
 * The Onix launcher writes them to config/onix/pack-folders.txt before every start; they show up in the pack menu.
 */
public final class ExtraPackFolders {
    private ExtraPackFolders() {}

    public static List<Path> read() {
        List<Path> out = new ArrayList<>();
        try {
            Path list = FabricLoader.getInstance().getConfigDir().resolve("onix").resolve("pack-folders.txt");
            if (!Files.isRegularFile(list)) return out;
            Path own = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").toAbsolutePath().normalize();
            for (String line : Files.readAllLines(list)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Path p = Path.of(line).toAbsolutePath().normalize();
                if (!p.equals(own) && Files.isDirectory(p) && !out.contains(p)) out.add(p);
            }
        } catch (Exception ignored) {
            // a broken list never stops the game
        }
        return out;
    }
}
