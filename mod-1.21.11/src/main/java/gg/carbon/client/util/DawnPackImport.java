package gg.carbon.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.carbon.client.CarbonClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Imports a Dawn launcher profile export (.dawnpack): a zip with {@code dawnpack.json} (mods referenced
 * by Modrinth project/version id and hashes) plus an {@code overrides/} folder that mirrors the game
 * directory. Mods are downloaded from Modrinth and hash-checked. When the pack targets another Minecraft
 * version, each mod is resolved to its newest Fabric build for the running version instead.
 * Nothing that already exists is overwritten; changes apply after a restart.
 */
public final class DawnPackImport {
	private DawnPackImport() {}

	private static final String API = "https://api.modrinth.com/v2";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	private static volatile String status = "Install the mods of a Dawn profile export (.dawnpack)";
	private static volatile boolean running;

	/** Shown as the description of the import row in the mod menu. */
	public static String status() {
		return status;
	}

	public static boolean running() {
		return running;
	}

	/** Opens a file picker and imports the chosen pack on a background thread. */
	public static void pickAndRun() {
		if (running) return;
		running = true;
		Thread t = new Thread(() -> {
			try {
				Path pack = choose();
				if (pack == null) {
					status = "Import cancelled";
					return;
				}
				run(pack);
			} catch (Exception e) {
				CarbonClient.LOG.error("Dawn import failed", e);
				status = "Import failed: " + e.getMessage();
				toast("Dawn import failed", e.getMessage());
			} finally {
				running = false;
			}
		}, "Onix Dawn import");
		t.setDaemon(true);
		t.start();
	}

	private static Path choose() {
		Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
		String start = (Files.isDirectory(downloads) ? downloads : Path.of(System.getProperty("user.home"))).toString() + java.io.File.separator;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8("*.dawnpack")).flip();
			String file = TinyFileDialogs.tinyfd_openFileDialog("Import Dawn pack", start, filters, "Dawn pack (*.dawnpack)", false);
			return file == null ? null : Path.of(file);
		}
	}

	// ------------------------------------------------------------------ import

	private record Ref(String fileName, String type, String projectId, String versionId, String sha1, boolean enabled) {}

	public static void run(Path pack) throws Exception {
		Path game = FabricLoader.getInstance().getGameDir();
		String mcVersion = SharedConstants.getCurrentVersion().name();
		status = "Reading " + pack.getFileName() + "…";

		try (ZipFile zip = new ZipFile(pack.toFile())) {
			ZipEntry manifest = zip.getEntry("dawnpack.json");
			if (manifest == null) throw new IOException("not a Dawn pack (no dawnpack.json)");
			JsonObject root;
			try (InputStream in = zip.getInputStream(manifest)) {
				root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			}
			JsonObject profile = root.has("profile") ? root.getAsJsonObject("profile") : new JsonObject();
			String packName = str(profile, "name", pack.getFileName().toString());
			String packVersion = str(profile, "minecraftVersion", mcVersion);
			boolean sameVersion = packVersion.equals(mcVersion);

			List<Ref> refs = new ArrayList<>();
			for (JsonElement el : arr(root, "contentReferences")) {
				JsonObject o = el.getAsJsonObject();
				String project = null, version = null;
				for (JsonElement p : arr(o, "providerCandidates")) {
					JsonObject po = p.getAsJsonObject();
					if ("Modrinth".equalsIgnoreCase(str(po, "provider", ""))) {
						project = str(po, "projectId", null);
						version = str(po, "versionId", null);
						break;
					}
				}
				JsonObject hashes = o.has("hashes") ? o.getAsJsonObject("hashes") : new JsonObject();
				refs.add(new Ref(str(o, "fileName", null), str(o, "type", "Mod"), project, version, str(hashes, "sha1", null),
					!o.has("enabled") || o.get("enabled").getAsBoolean()));
			}

			// Mods that are already installed (by Modrinth project) are left alone, so no duplicates appear.
			Path mods = game.resolve("mods");
			Files.createDirectories(mods);
			Set<String> installedProjects = installedProjects(mods);

			int added = 0, present = 0;
			List<String> missing = new ArrayList<>();
			Map<String, JsonObject> exact = sameVersion ? versions(refs) : Map.of();
			for (int i = 0; i < refs.size(); i++) {
				Ref r = refs.get(i);
				status = "Installing " + (i + 1) + "/" + refs.size() + ": " + (r.fileName() == null ? r.projectId() : r.fileName());
				if (r.projectId() == null) {
					missing.add(r.fileName());
					continue;
				}
				if (installedProjects.contains(r.projectId())) {
					present++;
					continue;
				}
				JsonObject version = sameVersion ? exact.get(r.versionId()) : newestFor(r.projectId(), mcVersion);
				JsonObject file = version == null ? null : pickFile(version, sameVersion ? r.sha1() : null);
				if (file == null) {
					missing.add(r.fileName() != null ? r.fileName() : r.projectId());
					continue;
				}
				Path dir = game.resolve(folderFor(r.type()));
				Files.createDirectories(dir);
				String name = safeName(str(file, "filename", r.fileName()));
				Path target = dir.resolve(r.enabled() ? name : name + ".disabled");
				if (Files.exists(target) || Files.exists(dir.resolve(name))) {
					present++;
					continue;
				}
				try {
					download(str(file, "url", null), file.getAsJsonObject("hashes").get("sha1").getAsString(), target);
				} catch (Exception e) {
					CarbonClient.LOG.warn("Dawn import: could not download {}", name, e);
					missing.add(name);
					continue;
				}
				installedProjects.add(r.projectId());
				added++;
			}

			int extracted = extractOverrides(zip, game, sameVersion);

			StringBuilder msg = new StringBuilder();
			msg.append(added).append(" added, ").append(present).append(" already installed");
			if (extracted > 0) msg.append(", ").append(extracted).append(" files copied");
			if (!missing.isEmpty()) msg.append(", ").append(missing.size()).append(" not available for ").append(mcVersion);
			status = packName + ": " + msg + " — restart Minecraft to apply";
			if (!missing.isEmpty()) CarbonClient.LOG.warn("Dawn import: no {} build for {}", mcVersion, missing);
			toast("Imported " + packName, msg + ". Restart to apply.");
		}
	}

	/** Exact versions referenced by the pack, fetched in one request. */
	private static Map<String, JsonObject> versions(List<Ref> refs) throws Exception {
		List<String> ids = refs.stream().map(Ref::versionId).filter(v -> v != null).distinct().toList();
		Map<String, JsonObject> out = new HashMap<>();
		for (int i = 0; i < ids.size(); i += 100) {
			JsonArray chunk = new JsonArray();
			ids.subList(i, Math.min(ids.size(), i + 100)).forEach(chunk::add);
			for (JsonElement v : getJson("/versions?ids=" + enc(chunk.toString())).getAsJsonArray()) {
				out.put(v.getAsJsonObject().get("id").getAsString(), v.getAsJsonObject());
			}
		}
		return out;
	}

	/** Newest Fabric build of a project for the given Minecraft version, or null. */
	private static JsonObject newestFor(String project, String mcVersion) throws Exception {
		String q = "/project/" + enc(project) + "/version?loaders=" + enc("[\"fabric\"]") + "&game_versions=" + enc("[\"" + mcVersion + "\"]");
		JsonArray list = getJson(q).getAsJsonArray();
		return list.isEmpty() ? null : list.get(0).getAsJsonObject();
	}

	private static JsonObject pickFile(JsonObject version, String sha1) {
		JsonArray files = arr(version, "files");
		JsonObject primary = null;
		for (JsonElement f : files) {
			JsonObject fo = f.getAsJsonObject();
			if (sha1 != null && sha1.equalsIgnoreCase(fo.getAsJsonObject("hashes").get("sha1").getAsString())) return fo;
			if (primary == null || (fo.has("primary") && fo.get("primary").getAsBoolean())) primary = fo;
		}
		return primary;
	}

	/** Modrinth project ids of the jars already in the mods folder (looked up by file hash). */
	private static Set<String> installedProjects(Path mods) {
		Set<String> projects = new HashSet<>();
		try (Stream<Path> files = Files.list(mods)) {
			JsonArray hashes = new JsonArray();
			for (Path p : files.toList()) {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (Files.isRegularFile(p) && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))) hashes.add(sha1(p));
			}
			if (hashes.isEmpty()) return projects;
			JsonObject body = new JsonObject();
			body.add("hashes", hashes);
			body.addProperty("algorithm", "sha1");
			JsonObject res = postJson("/version_files", body).getAsJsonObject();
			for (var e : res.entrySet()) projects.add(e.getValue().getAsJsonObject().get("project_id").getAsString());
		} catch (Exception e) {
			CarbonClient.LOG.warn("Dawn import: could not identify installed mods", e);
		}
		return projects;
	}

	/** Copies {@code overrides/**} into the game directory without replacing anything. */
	private static int extractOverrides(ZipFile zip, Path game, boolean sameVersion) throws IOException {
		int count = 0;
		Path base = game.toAbsolutePath().normalize();
		for (ZipEntry e : java.util.Collections.list(zip.entries())) {
			String name = e.getName().replace('\\', '/');
			if (e.isDirectory() || !name.startsWith("overrides/")) continue;
			String rel = name.substring("overrides/".length());
			// version specific jars only make sense for the version the pack was made for
			if (!sameVersion && rel.startsWith("mods/")) continue;
			Path target = base.resolve(rel).normalize();
			if (!target.startsWith(base) || Files.exists(target)) continue;
			Files.createDirectories(target.getParent());
			try (InputStream in = zip.getInputStream(e)) {
				Files.copy(in, target);
			}
			count++;
		}
		return count;
	}

	private static String folderFor(String type) {
		String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
		if (t.contains("resource")) return "resourcepacks";
		if (t.contains("shader")) return "shaderpacks";
		return "mods";
	}

	// ------------------------------------------------------------------ http + io

	private static HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(30))
			.header("User-Agent", "onix-client/" + CarbonClient.VERSION + " (dawnpack import)");
	}

	private static JsonElement getJson(String path) throws Exception {
		HttpResponse<String> res = HTTP.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new IOException("Modrinth returned " + res.statusCode());
		return JsonParser.parseString(res.body());
	}

	private static JsonElement postJson(String path, JsonObject body) throws Exception {
		HttpResponse<String> res = HTTP.send(request(path).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new IOException("Modrinth returned " + res.statusCode());
		return JsonParser.parseString(res.body());
	}

	private static void download(String url, String sha1, Path target) throws Exception {
		if (url == null || !url.startsWith("https://")) throw new IOException("bad download url " + url);
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		HttpResponse<Path> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
			.header("User-Agent", "onix-client/" + CarbonClient.VERSION).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp));
		if (res.statusCode() != 200) {
			Files.deleteIfExists(tmp);
			throw new IOException("download failed (" + res.statusCode() + "): " + url);
		}
		if (!sha1(tmp).equalsIgnoreCase(sha1)) {
			Files.deleteIfExists(tmp);
			throw new IOException("hash mismatch for " + target.getFileName());
		}
		Files.move(tmp, target);
	}

	private static String sha1(Path p) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		try (InputStream in = Files.newInputStream(p)) {
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
		}
		return HexFormat.of().formatHex(md.digest());
	}

	private static String safeName(String name) {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("missing file name");
		String n = Path.of(name).getFileName().toString();
		if (n.equals("..") || n.contains("/") || n.contains("\\")) throw new IllegalArgumentException("bad file name " + name);
		return n;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	private static JsonArray arr(JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	private static void toast(String title, String text) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			Component.literal(title), Component.literal(text)));
	}
}
