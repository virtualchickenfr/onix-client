package gg.carbon.client.screen.menu;

import java.util.ArrayList;
import java.util.List;

/** Clickable rectangles recorded while a theme renders, used for hit testing on the next click. */
public final class HitList {
	private record Hit(int entry, float x, float y, float w, float h) {}

	private final List<Hit> hits = new ArrayList<>();

	public void clear() {
		hits.clear();
	}

	public void add(int entry, float x, float y, float w, float h) {
		hits.add(new Hit(entry, x, y, w, h));
	}

	public int at(float mx, float my) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) return h.entry();
		}
		return -1;
	}
}
