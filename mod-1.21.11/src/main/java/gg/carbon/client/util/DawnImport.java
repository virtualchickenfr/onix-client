package gg.carbon.client.util;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.pvp.ToggleSprint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * Imports what a Dawn installation keeps on disk: its copy of the game options
 * ({@code feather/mc_options_v2.txt} — keybinds, sensitivity, FOV, video, sound, packs), the server
 * list and the resource packs it uses. Dawn's own module settings are stored in the Dawn account,
 * not on disk, so they cannot be read.
 */
public final class DawnImport {
	private DawnImport() {}

	private static final String OPTIONS = "feather/mc_options_v2.txt";

	/** Options that describe this machine/window or one-off state rather than preferences. */
	private static final Set<String> SKIP = Set.of("version", "fullscreen", "exclusiveFullscreen", "overrideWidth", "overrideHeight",
		"glDebugVerbosity", "startedCleanly", "onboardAccessibility", "tutorialStep", "joinedFirstServer", "skipMultiplayerWarning",
		"soundDevice", "preferredGraphicsBackend", "macFullscreenMenuVisibility", "syncChunkWrites", "useNativeTransport");

	public record Result(Path source, int options, int keys, int servers, int packs, boolean sprint, String error) {
		public String summary() {
			if (error != null) return error;
			List<String> parts = new ArrayList<>();
			parts.add(options + " options");
			parts.add(keys + " keybinds");
			parts.add(servers + (servers == 1 ? " server" : " servers"));
			parts.add(packs + (packs == 1 ? " pack" : " packs"));
			if (sprint) parts.add("toggle sprint");
			return "Imported from " + instanceName(source) + ": " + String.join(", ", parts);
		}
	}

	// ------------------------------------------------------------------ discovery

	/** Game directories that contain a Dawn (Feather based) options file, newest first. */
	public static List<Path> find(Minecraft mc) {
		List<Path> roots = new ArrayList<>();
		roots.add(mc.gameDirectory.toPath());
		String appData = System.getenv("APPDATA");
		String home = System.getProperty("user.home");
		if (appData != null) {
			Path a = Path.of(appData);
			roots.add(a.resolve(".minecraft"));
			children(a.resolve("PrismLauncher/instances"), roots, "minecraft", ".minecraft");
			children(a.resolve("MultiMC/instances"), roots, "minecraft", ".minecraft");
			children(a.resolve("ModrinthApp/profiles"), roots, "");
			children(a.resolve("com.modrinth.theseus/profiles"), roots, "");
			children(a.resolve(".feather"), roots, "");
		}
		if (home != null) {
			Path h = Path.of(home);
			roots.add(h.resolve(".minecraft"));
			children(h.resolve("curseforge/minecraft/Instances"), roots, "");
		}
		return roots.stream()
			.filter(r -> Files.isRegularFile(r.resolve(OPTIONS)))
			.distinct()
			// prefer real Dawn installs, then the most recently used one
			.sorted(Comparator.<Path, Boolean>comparing(r -> !(Files.isDirectory(r.resolve("dawn")) || Files.isDirectory(r.resolve("dawn-loader"))))
				.thenComparing(r -> -modified(r.resolve(OPTIONS))))
			.toList();
	}

	private static void children(Path dir, List<Path> out, String... subs) {
		if (!Files.isDirectory(dir)) return;
		try (Stream<Path> s = Files.list(dir)) {
			s.filter(Files::isDirectory).forEach(p -> {
				for (String sub : subs) out.add(sub.isEmpty() ? p : p.resolve(sub));
			});
		} catch (IOException ignored) {
		}
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	static String instanceName(Path root) {
		Path name = root.getFileName();
		if (name == null) return root.toString();
		String n = name.toString();
		if ((n.equals("minecraft") || n.equals(".minecraft")) && root.getParent() != null && !root.getParent().toString().endsWith("Roaming")) {
			return "\"" + root.getParent().getFileName() + "\"";
		}
		return "\"" + n + "\"";
	}

	// ------------------------------------------------------------------ import

	public static Result run(Minecraft mc) {
		List<Path> found = find(mc);
		if (found.isEmpty()) return new Result(null, 0, 0, 0, 0, false, "No Dawn installation found on this PC");
		Path src = found.getFirst();
		try {
			int[] opts = options(mc, src);
			int servers = servers(mc, src);
			int packs = packs(mc, src);
			boolean sprint = opts[2] == 1;
			if (opts[0] > 0 || packs > 0) applyPacks(mc);
			CarbonClient.config().save();
			CarbonClient.LOG.info("Dawn import from {}: {} options, {} keys, {} servers, {} packs", src, opts[0], opts[1], servers, packs);
			return new Result(src, opts[0], opts[1], servers, packs, sprint, null);
		} catch (Exception e) {
			CarbonClient.LOG.error("Dawn import failed", e);
			return new Result(src, 0, 0, 0, 0, false, "Import failed: " + e.getMessage());
		}
	}

	/** Merges Dawn's options into options.txt and reloads them. Returns {changed, keybinds, sprint}. */
	private static int[] options(Minecraft mc, Path src) throws IOException {
		mc.options.save();
		Path file = mc.options.getFile().toPath();
		Map<String, String> current = read(file);
		Map<String, String> dawn = read(src.resolve(OPTIONS));
		int changed = 0, keys = 0, sprint = 0;
		for (var e : dawn.entrySet()) {
			String key = e.getKey(), value = e.getValue();
			if (SKIP.contains(key)) continue;
			if (key.equals("toggleSprint") && value.equals("true")) {
				// Carbon's Toggle Sprint takes over, the vanilla toggle would fight it
				ToggleSprint ts = CarbonClient.modules().get(ToggleSprint.class);
				if (!ts.enabled()) ts.setEnabled(true);
				sprint = 1;
				value = "false";
			}
			if (value.equals(current.get(key))) continue;
			current.put(key, value);
			changed++;
			if (key.startsWith("key_")) keys++;
		}
		StringBuilder sb = new StringBuilder();
		for (var e : current.entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		mc.options.load();
		mc.options.save();
		return new int[] {changed, keys, sprint};
	}

	private static Map<String, String> read(Path file) throws IOException {
		Map<String, String> map = new LinkedHashMap<>();
		if (!Files.isRegularFile(file)) return map;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			int i = line.indexOf(':');
			if (i <= 0) continue;
			map.put(line.substring(0, i), line.substring(i + 1));
		}
		return map;
	}

	/** Adds Dawn's servers that are not in the list yet. */
	private static int servers(Minecraft mc, Path src) throws IOException {
		Path file = src.resolve("servers.dat");
		if (!Files.isRegularFile(file) || src.equals(mc.gameDirectory.toPath())) return 0;
		CompoundTag tag = NbtIo.read(file);
		if (tag == null) return 0;
		ServerList list = new ServerList(mc);
		list.load();
		int[] added = {0};
		// Dawn keeps every server you joined as a "hidden" (direct connect) entry and shows them in its own UI,
		// so they come over as normal, visible entries named after their address
		tag.getListOrEmpty("servers").compoundStream().forEach(t -> {
			ServerData data = ServerData.read(t);
			if (data.ip == null || data.ip.isBlank()) return;
			ServerData existing = list.get(data.ip);
			if (existing != null) {
				ServerData shown = list.unhide(data.ip);
				if (shown != null) {
					if (shown.name == null || shown.name.equals("Minecraft Server")) shown.name = shown.ip;
					added[0]++;
				}
				return;
			}
			if (data.name == null || data.name.isBlank() || data.name.equals("Minecraft Server")) data.name = data.ip;
			list.add(data, false);
			added[0]++;
		});
		if (added[0] > 0) list.save();
		return added[0];
	}

	/** Copies the resource packs Dawn has enabled into this game's resourcepacks folder. */
	private static int packs(Minecraft mc, Path src) throws IOException {
		Map<String, String> dawn = read(src.resolve(OPTIONS));
		String list = dawn.getOrDefault("resourcePacks", "[]");
		Path from = src.resolve("resourcepacks"), to = mc.gameDirectory.toPath().resolve("resourcepacks");
		if (from.toAbsolutePath().normalize().equals(to.toAbsolutePath().normalize())) return 0;
		int copied = 0;
		for (String raw : list.replace("[", "").replace("]", "").split(",")) {
			String id = raw.trim().replace("\"", "");
			if (!id.startsWith("file/")) continue;
			String name = id.substring(5);
			Path s = from.resolve(name), d = to.resolve(name);
			if (!Files.exists(s) || Files.exists(d)) continue;
			Files.createDirectories(to);
			copy(s, d);
			copied++;
		}
		return copied;
	}

	private static void copy(Path s, Path d) throws IOException {
		if (Files.isDirectory(s)) {
			try (Stream<Path> walk = Files.walk(s)) {
				for (Path p : (Iterable<Path>) walk::iterator) {
					Path target = d.resolve(s.relativize(p).toString());
					if (Files.isDirectory(p)) Files.createDirectories(target);
					else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} else {
			Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Re-scans the pack folder and applies the imported pack selection. */
	private static void applyPacks(Minecraft mc) {
		var repo = mc.getResourcePackRepository();
		List<String> before = List.copyOf(repo.getSelectedIds());
		repo.reload();
		mc.options.loadSelectedResourcePacks(repo);
		if (!List.copyOf(repo.getSelectedIds()).equals(before)) mc.reloadResourcePacks();
		mc.options.save();
	}
}
