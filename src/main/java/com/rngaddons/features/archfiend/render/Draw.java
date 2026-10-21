package com.rngaddons.features.archfiend.render;

/** Small drawing helpers built only on {@link Canvas#rect}. */
final class Draw {
	private Draw() {}

	static int alpha(int argb, float a) {
		int na = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, a)));
		return (na << 24) | (argb & 0xFFFFFF);
	}

	/** Filled disc; {@code step} is the row height (2 is plenty for soft glows). */
	static void disc(Canvas c, int cx, int cy, int r, int argb, int step) {
		if (r <= 0 || (argb >>> 24) == 0) return;
		for (int dy = -r; dy < r; dy += step) {
			float yy = dy + step / 2f;
			int hw = (int) Math.sqrt(Math.max(0f, r * r - yy * yy));
			if (hw <= 0) continue;
			c.rect(cx - hw, cy + dy, hw * 2, Math.min(step, r - dy), argb);
		}
	}

	/** Ring between radii r0 (inner) and r1 (outer). */
	static void ring(Canvas c, int cx, int cy, int r0, int r1, int argb) {
		if (r1 <= 0 || (argb >>> 24) == 0) return;
		for (int dy = -r1; dy < r1; dy++) {
			float yy = dy + 0.5f;
			int hw1 = (int) Math.sqrt(Math.max(0f, r1 * r1 - yy * yy));
			if (hw1 <= 0) continue;
			if (Math.abs(yy) < r0) {
				int hw0 = (int) Math.sqrt(r0 * r0 - yy * yy);
				if (hw1 > hw0) {
					c.rect(cx - hw1, cy + dy, hw1 - hw0, 1, argb);
					c.rect(cx + hw0, cy + dy, hw1 - hw0, 1, argb);
				}
			} else {
				c.rect(cx - hw1, cy + dy, hw1 * 2, 1, argb);
			}
		}
	}

	/** Soft glow made of a few stacked translucent discs. strength 0..1 scales the alpha. */
	static void glow(Canvas c, int cx, int cy, int radius, int rgb, float strength) {
		if (strength <= 0.01f) return;
		for (int k = 0; k < 4; k++) {
			float f = 1f - k * 0.22f;
			int a = Math.round(255 * strength * 0.16f);
			disc(c, cx, cy, Math.round(radius * f), (a << 24) | (rgb & 0xFFFFFF), 2);
		}
	}

	static void centerText(Canvas c, String s, float cx, float y, float scale, int argb, boolean shadow) {
		if ((argb >>> 24) < 4) return;
		c.push();
		c.translate(cx, y);
		c.scale(scale);
		c.text(s, -c.textWidth(s) / 2, 0, argb, shadow);
		c.pop();
	}

	static float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	static float easeOutCubic(float u) {
		float i = 1f - clamp01(u);
		return 1f - i * i * i;
	}

	static float easeOutBack(float u) {
		u = clamp01(u);
		float c1 = 1.70158f, c3 = c1 + 1f;
		float v = u - 1f;
		return 1f + c3 * v * v * v + c1 * v * v;
	}
}
