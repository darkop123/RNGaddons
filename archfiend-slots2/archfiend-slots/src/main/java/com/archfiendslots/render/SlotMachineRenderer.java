package com.archfiendslots.render;

import com.archfiendslots.core.RollSession;
import com.archfiendslots.core.Settings;

/**
 * Draws the slot machine overlay for one {@link RollSession}. Everything is a pure function of the session
 * and the current time (monotonic milliseconds), so the speed never depends on the frame rate.
 * Design space: origin at the payline centre, units scale uniformly to the screen.
 */
public final class SlotMachineRenderer {
	static final int FRAME_X = -84, FRAME_TOP = -56, FRAME_W = 168, FRAME_BOTTOM = 32;
	static final int[] WIN_X = {-52, 0, 52};
	static final int WIN_W = 46, WIN_TOP = -30, WIN_H = 54;
	static final int CELL = 18, PX = 2;
	private static final float DESIGN_H = 130f, DESIGN_W = 230f;

	private final WinEffectRenderer fx = new WinEffectRenderer();

	/** Drop all visual state (called when nothing is active, e.g. after a disconnect). */
	public void reset() {
		fx.reset();
	}

	public void render(Canvas c, RollSession s, long now, Settings cfg) {
		if (s == null) {
			fx.reset();
			return;
		}
		float alpha = s.alpha(now);
		if (alpha <= 0.01f) return;

		int sw = c.width(), sh = c.height();
		float sc = (float) (Math.min(sh * 0.40 / DESIGN_H, sw * 0.72 / DESIGN_W) * cfg.overlayScale);
		sc = Math.max(0.4f, sc) * s.introScale(now);

		fx.prepare(s, now, cfg);
		fx.drawBackdrop(c, s, now, alpha);

		boolean hc = s.dice.highClass;
		long t = s.elapsed(now);

		c.push();
		c.translate(sw / 2f + fx.shakeX() * sc, sh / 2f - 9f * sc + fx.shakeY() * sc);
		c.scale(sc);

		fx.drawBehind(c, s, now, alpha, hc);
		drawFrame(c, s, alpha, hc);
		for (int i = 0; i < 3; i++) drawReel(c, s, i, t, alpha);
		drawPayline(c, alpha);
		fx.drawFront(c, s, now, alpha);
		drawStatus(c, s, now, alpha);
		fx.drawTitles(c, s, now, alpha, 0);
		if (s.demo) {
			Draw.centerText(c, "DEMO - NOT A REAL ROLL", 0, FRAME_TOP - 12, 1.0f, Draw.alpha(0xFFFF7070, alpha), true);
		}
		c.pop();
	}

	// ------------------------------------------------------------------ pieces

	private void drawFrame(Canvas c, RollSession s, float a, boolean hc) {
		int gold = hc ? 0xFFFFD75E : 0xFFC9A227;
		int goldDark = hc ? 0xFF9C7A1A : 0xFF7A6218;
		int body = hc ? 0xFF07040C : 0xFF0F0C14;
		int red = hc ? 0xFF8E1428 : 0xFF5A0F1A;
		int x = FRAME_X, y = FRAME_TOP, w = FRAME_W, h = FRAME_BOTTOM - FRAME_TOP;

		c.rect(x - 2, y - 2, w + 4, h + 4, Draw.alpha(0xFF000000, 0.55f * a)); // soft shadow
		c.rect(x, y, w, h, Draw.alpha(body, 0.96f * a));
		// metallic gold border (two tones)
		c.rect(x, y, w, 2, Draw.alpha(gold, a));
		c.rect(x, y + h - 2, w, 2, Draw.alpha(goldDark, a));
		c.rect(x, y, 2, h, Draw.alpha(gold, a));
		c.rect(x + w - 2, y, 2, h, Draw.alpha(goldDark, a));
		// inner dark-red line
		c.rect(x + 3, y + 3, w - 6, 1, Draw.alpha(red, a));
		c.rect(x + 3, y + h - 4, w - 6, 1, Draw.alpha(red, a));
		c.rect(x + 3, y + 3, 1, h - 6, Draw.alpha(red, a));
		c.rect(x + w - 4, y + 3, 1, h - 6, Draw.alpha(red, a));
		if (hc) {
			c.rect(x + 5, y + 5, w - 10, 1, Draw.alpha(goldDark, 0.8f * a));
			c.rect(x + 5, y + h - 6, w - 10, 1, Draw.alpha(goldDark, 0.8f * a));
		}
		// corner studs
		int[][] studs = {{x - 1, y - 1}, {x + w - 4, y - 1}, {x - 1, y + h - 4}, {x + w - 4, y + h - 4}};
		for (int[] st : studs) {
			c.rect(st[0], st[1], 5, 5, Draw.alpha(gold, a));
			c.rect(st[0] + 1, st[1] + 1, 3, 3, Draw.alpha(hc ? 0xFFE02040 : 0xFF8E1428, a));
		}
		// title
		Draw.centerText(c, s.dice.title, 0, y + 7, 1.0f, Draw.alpha(hc ? 0xFFFFE08A : 0xFFE6C766, a), true);
		c.rect(x + 12, y + 18, w - 24, 1, Draw.alpha(goldDark, 0.7f * a));
	}

	private void drawReel(Canvas c, RollSession s, int i, long t, float a) {
		SlotReel reel = s.reel(i);
		int x0 = WIN_X[i] - WIN_W / 2;
		// window well + background
		c.rect(x0 - 2, WIN_TOP - 2, WIN_W + 4, WIN_H + 4, Draw.alpha(0xFF2A2230, a));
		c.rect(x0, WIN_TOP, WIN_W, WIN_H, Draw.alpha(0xFF0A0810, a));

		double p = reel.position(t);
		int base = (int) Math.floor(p);
		float frac = (float) (p - base);
		int alpha255 = Math.round(255 * a);
		boolean spinning = reel.isSpinning(t);
		for (int k = -2; k <= 2; k++) {
			float cy = WinEffectRenderer.CY + (k - frac) * CELL;
			float top = cy - 8;
			if (top > WIN_TOP + WIN_H || top + 16 < WIN_TOP) continue;
			int ti = (int) Math.floor(top);
			float f = top - ti;
			c.push();
			c.translate(0, f);
			Sprites.drawSymbol(c, reel.symbolAt(base + k), x0 + (WIN_W - 16) / 2, ti, PX,
				Math.round(WIN_TOP - f), Math.round(WIN_TOP + WIN_H - f), alpha255);
			c.pop();
		}
		// depth shading at top and bottom
		int[] shade = {0xB0, 0x88, 0x55, 0x2A};
		for (int k = 0; k < shade.length; k++) {
			int col = (shade[k] * Math.round(255 * a) / 255) << 24 | 0x0A0810;
			c.rect(x0, WIN_TOP + k * 3, WIN_W, 3, col);
			c.rect(x0, WIN_TOP + WIN_H - (k + 1) * 3, WIN_W, 3, col);
		}
		if (spinning) {
			// faint motion streaks while spinning fast
			c.rect(x0 + 4, WIN_TOP, 1, WIN_H, Draw.alpha(0xFFFFFFFF, 0.05f * a));
			c.rect(x0 + WIN_W - 5, WIN_TOP, 1, WIN_H, Draw.alpha(0xFFFFFFFF, 0.05f * a));
		}
	}

	private void drawPayline(Canvas c, float a) {
		int y = WinEffectRenderer.CY;
		c.rect(WIN_X[0] - WIN_W / 2, y, WIN_X[2] + WIN_W / 2 - (WIN_X[0] - WIN_W / 2), 1, Draw.alpha(0xFFFF2A4A, 0.28f * a));
		// small arrows at both sides
		for (int d = 0; d < 3; d++) {
			c.rect(FRAME_X + 5 + d, y - 3 + d, 1, 7 - 2 * d, Draw.alpha(0xFFFF4A5A, a));
			c.rect(FRAME_X + FRAME_W - 6 - d, y - 3 + d, 1, 7 - 2 * d, Draw.alpha(0xFFFF4A5A, a));
		}
	}

	private void drawStatus(Canvas c, RollSession s, long now, float a) {
		RollSession.Kind kind = s.kind();
		long e = s.sinceLock(now);
		if (!s.isResolved() || e < 0) {
			Draw.centerText(c, "SPINNING...", 0, 40, 1.0f, Draw.alpha(0xFFB8A878, a), true);
			return;
		}
		float u = Draw.clamp01(e / 150f);
		switch (kind) {
			case NORMAL -> Draw.centerText(c, "RESULT: " + s.result(), 0, 40, 1.0f, Draw.alpha(0xFFD8D0C0, a * u), true);
			case UNKNOWN -> Draw.centerText(c, "RESULT NOT DETECTED", 0, 40, 1.0f, Draw.alpha(0xFF9A9A9A, a * u), true);
			default -> { /* GOLD / DYE: titles are drawn by WinEffectRenderer */ }
		}
	}
}
