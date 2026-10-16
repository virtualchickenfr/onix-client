package gg.carbon.client.module.impl.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** Waypoints rendered as floating markers projected onto the screen. */
public class Waypoints extends Module {
	private final KeySetting addKey = add(new KeySetting("Add Key", "Create a waypoint at your position", InputConstants.KEY_B));
	private final KeySetting removeKey = add(new KeySetting("Remove Key", "Remove the closest waypoint", 0));
	private final BoolSetting distanceText = add(new BoolSetting("Show Distance", "Show the distance under the name", true));
	private final BoolSetting deathPoint = add(new BoolSetting("Death Waypoints", "Create a waypoint where you die", true));
	private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", "Hide waypoints further than this", 10000, 100, 100000, 100).suffix(" blocks"));

	private final List<Point> points = new ArrayList<>();
	private boolean wasDead;

	public Waypoints() {
		super("waypoints", "Waypoints", "Mark places in the world and find your way back", Category.UTILITY);
	}

	public record Point(String name, String world, String dimension, double x, double y, double z) {}

	private String worldKey() {
		if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
		if (mc.getSingleplayerServer() != null) return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		return "unknown";
	}

	private String dimension() {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	@Override
	public boolean onKey(int key, int action) {
		if (action != 1 || mc.player == null) return false;
		if (addKey.bound() && key == addKey.key()) {
			addPoint("Waypoint " + (points.size() + 1));
			return true;
		}
		if (removeKey.bound() && key == removeKey.key()) {
			Point nearest = null;
			double best = Double.MAX_VALUE;
			for (Point p : visible()) {
				double d = mc.player.position().distanceToSqr(p.x(), p.y(), p.z());
				if (d < best) {
					best = d;
					nearest = p;
				}
			}
			if (nearest != null) {
				points.remove(nearest);
				mc.player.displayClientMessage(Component.literal("§7Removed §f" + nearest.name()), true);
			}
			return true;
		}
		return false;
	}

	private void addPoint(String name) {
		Vec3 p = mc.player.position();
		points.add(new Point(name, worldKey(), dimension(), Math.floor(p.x) + 0.5, Math.floor(p.y), Math.floor(p.z) + 0.5));
		mc.player.displayClientMessage(Component.literal("§7Added §f" + name), true);
		gg.carbon.client.CarbonClient.config().saveSoon();
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead && deathPoint.on()) addPoint("Death");
		wasDead = dead;
	}

	private List<Point> visible() {
		List<Point> list = new ArrayList<>();
		String w = worldKey(), d = dimension();
		for (Point p : points) if (p.world().equals(w) && p.dimension().equals(d)) list.add(p);
		return list;
	}

	public void render(GuiGraphics g) {
		if (mc.player == null || mc.level == null) return;
		Camera cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		Vector3fc f = cam.forwardVector(), up = cam.upVector(), left = cam.leftVector();
		float sw = g.guiWidth(), sh = g.guiHeight();
		double tanHalf = Math.tan(Math.toRadians(((gg.carbon.client.mixin.GameRendererInvoker) mc.gameRenderer).carbon$getFov(cam, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), true)) / 2.0);
		double scale = (sh / 2.0) / tanHalf;
		for (Point p : visible()) {
			double rx = p.x() - cp.x, ry = p.y() + 1.2 - cp.y, rz = p.z() - cp.z;
			double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (dist > maxDistance.get()) continue;
			double z = rx * f.x() + ry * f.y() + rz * f.z();
			if (z < 0.1) continue;
			double x = rx * left.x() + ry * left.y() + rz * left.z();
			double y = rx * up.x() + ry * up.y() + rz * up.z();
			float px = (float) (sw / 2.0 - x / z * scale);
			float py = (float) (sh / 2.0 - y / z * scale);
			if (px < -50 || px > sw + 50 || py < -50 || py > sh + 50) continue;

			float centerDist = (float) Math.hypot(px - sw / 2f, py - sh / 2f);
			float focus = 1f - Math.min(1f, centerDist / 120f);
			String name = p.name();
			String sub = String.format(Locale.ROOT, "%.0fm", dist);
			float nw = Fonts.width(name, true, 1f);
			float w = Math.max(nw, distanceText.on() ? Fonts.width(sub, false, 0.75f) : 0) + 12;
			float h = distanceText.on() ? 22 : 14;
			R2D.pushAlpha(0.55f + 0.45f * focus);
			R2D.circle(g, px, py + h / 2f + 6, 2.5f, Theme.WHITE);
			R2D.rrect(g, px - w / 2f, py - h / 2f - 2, w, h, 4, 0xB00A0A0C);
			R2D.outline(g, px - w / 2f, py - h / 2f - 2, w, h, 4, 0.5f, 0x33FFFFFF);
			Fonts.draw(g, name, px - nw / 2f, py - h / 2f + 1.5f, Theme.WHITE, true, 1f, false);
			if (distanceText.on()) {
				Fonts.centered(g, sub, px, py - h / 2f + 11.5f, Theme.TEXT_DIM, false, 0.75f);
			}
			R2D.popAlpha();
		}
	}

	@Override
	public JsonObject saveExtra() {
		JsonObject o = new JsonObject();
		JsonArray arr = new JsonArray();
		for (Point p : points) {
			JsonObject j = new JsonObject();
			j.addProperty("name", p.name());
			j.addProperty("world", p.world());
			j.addProperty("dim", p.dimension());
			j.addProperty("x", p.x());
			j.addProperty("y", p.y());
			j.addProperty("z", p.z());
			arr.add(j);
		}
		o.add("points", arr);
		return o;
	}

	@Override
	public void loadExtra(JsonObject o) {
		points.clear();
		if (!o.has("points")) return;
		for (JsonElement e : o.getAsJsonArray("points")) {
			JsonObject j = e.getAsJsonObject();
			points.add(new Point(j.get("name").getAsString(), j.get("world").getAsString(), j.get("dim").getAsString(),
				j.get("x").getAsDouble(), j.get("y").getAsDouble(), j.get("z").getAsDouble()));
		}
	}
}
