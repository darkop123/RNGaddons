package com.rngaddons.features.archfiend.render;

import com.rngaddons.features.archfiend.core.RollSession;
import com.rngaddons.features.archfiend.core.Settings;

/**
 * Gold-jackpot (6) and Archfiend-Dye-jackpot (7) effects. Only ever driven by a session whose result was
 * confirmed (RollSession.kind() == GOLD / DYE); for everything else nothing here draws anything special.
 *
 * All times are relative to the moment the last reel locks in ("e", milliseconds).
 */
final class WinEffectRenderer {
	static final int CX = 0;
	static final int CY = -3; // payline / window centre in design space

	private static final int GOLD = 0xFFFFC72C;
	private static final int GOLD_LIGHT = 0xFFFFF0A0;
	private static final int RED = 0xFFE02040;
	private static final int VIOLET = 0xFF9B3BE0;

	private final ParticleSystem ps = new ParticleSystem();
	private long sessionId = -1;
	private long lastNow;
	private boolean fired0, fired1, fired2;
	private long lastEmber;
	private float shakeX, shakeY;

	void reset() {
		ps.clear();
		sessionId = -1;
		shakeX = shakeY = 0f;
	}

	float shakeX() { return shakeX; }
	float shakeY() { return shakeY; }

	/** Advance particles and trigger bursts. Call once per frame before drawing. */
	void prepare(RollSession s, long now, Settings cfg) {
		if (s.id != sessionId) {
			ps.clear();
			sessionId = s.id;
			lastNow = now;
			fired0 = fired1 = fired2 = false;
			lastEmber = Long.MIN_VALUE;
		}
		float dt = Math.max(0, Math.min(50, now - lastNow)) / 1000f;
		lastNow = now;
		ps.update(dt);
		shakeX = shakeY = 0f;

		RollSession.Kind kind = s.kind();
		if (kind != RollSession.Kind.GOLD && kind != RollSession.Kind.DYE) return;
		long e = s.sinceLock(now);
		if (e < 0) return;

		if (cfg.particlesEnabled) {
			if (kind == RollSession.Kind.GOLD) {
				if (!fired0) {
					fired0 = true;
					ps.burst(CX, CY, 30, 45f, 125f, 0.9f, 3f, 150f, GOLD, GOLD_LIGHT);
				}
				if (!fired1 && e >= 280) {
					fired1 = true;
					ps.burst(CX, CY, 14, 30f, 80f, 0.8f, 2f, 120f, GOLD, 0xFFFFFFFF);
				}
			} else {
				if (!fired0 && e >= 450) {
					fired0 = true;
					ps.burst(CX, CY, 26, 55f, 135f, 0.8f, 2f, 40f, GOLD, GOLD_LIGHT);
				}
				if (e >= 300 && e <= 1000 && (lastEmber == Long.MIN_VALUE || now - lastEmber >= 36)) {
					lastEmber = now;
					float ang = ps.random() * 6.2831855f;
					ps.ember(CX, CY, 34f, ang, 0.7f, ps.random() < 0.5f ? RED : VIOLET);
				}
				if (!fired1 && e >= 900) {
					fired1 = true;
					ps.burst(CX, CY, 22, 40f, 100f, 0.6f, 2f, 20f, VIOLET, GOLD);
				}
			}
		}

		if (cfg.screenShakeEnabled) {
			float amp = 0f;
			if (kind == RollSession.Kind.GOLD) {
				amp = 1.6f * (float) Math.exp(-e / 150.0);
			} else {
				amp = 3.2f * (float) Math.exp(-e / 180.0);
				if (e >= 900) amp += 1.5f * (float) Math.exp(-(e - 900) / 80.0);
			}
			if (amp > 0.05f) {
				shakeX = amp * (float) Math.sin(e * 0.09 + 1.3);
				shakeY = amp * (float) Math.cos(e * 0.11);
			}
		}
	}

	/** Full-screen darkening for the dye jackpot (drawn in screen space, before any transform). */
	void drawBackdrop(Canvas c, RollSession s, long now, float alpha) {
		if (s.kind() != RollSession.Kind.DYE) return;
		long e = s.sinceLock(now);
		float t = Draw.clamp01((e + 250) / 450f);
		if (t <= 0f) return;
		c.rect(0, 0, c.width(), c.height(), Draw.alpha(0xFF050008, 0.55f * t * alpha));
	}

	/** Glow behind the machine frame. */
	void drawBehind(Canvas c, RollSession s, long now, float alpha, boolean highClass) {
		RollSession.Kind kind = s.kind();
		long e = s.sinceLock(now);
		if (kind == RollSession.Kind.GOLD && e >= 0) {
			float pulse = (float) Math.sin(Math.PI * Draw.clamp01(e / 420f));
			Draw.glow(c, CX, CY, 100, 0xFFC47A, 0.9f * pulse * alpha);
		} else if (kind == RollSession.Kind.DYE) {
			float t = Draw.clamp01((e + 250) / 600f);
			float breathe = e > 0 ? 0.08f * (float) Math.sin(e / 170.0) : 0f;
			Draw.glow(c, CX, CY, 118, 0x8B2BE0, (0.9f * t + breathe) * alpha);
			Draw.glow(c, CX, CY, 84, 0xE02040, (0.9f * t + breathe) * alpha);
		}
	}

	/** Everything drawn on top of the frame and reels. */
	void drawFront(Canvas c, RollSession s, long now, float alpha) {
		RollSession.Kind kind = s.kind();
		long e = s.sinceLock(now);
		if (e < 0 || (kind != RollSession.Kind.GOLD && kind != RollSession.Kind.DYE)) {
			ps.render(c, alpha);
			return;
		}

		if (kind == RollSession.Kind.GOLD) {
			if (e < 170) {
				Draw.glow(c, CX, CY, 78, 0xFFE9A0, 1.0f * (1f - e / 170f) * alpha);
			}
			// win outlines on the three windows
			float pulse = 0.55f + 0.45f * (float) Math.cos(Math.min(e, 600) / 600.0 * Math.PI);
			for (int i = 0; i < 3; i++) {
				int x0 = SlotMachineRenderer.WIN_X[i] - SlotMachineRenderer.WIN_W / 2;
				outline(c, x0 - 2, SlotMachineRenderer.WIN_TOP - 2, SlotMachineRenderer.WIN_W + 4,
					SlotMachineRenderer.WIN_H + 4, 2, Draw.alpha(GOLD_LIGHT, pulse * alpha));
			}
		} else {
			// controlled flicker of the reel windows (four short flashes)
			if (e < 360 && (e / 90) % 2 == 0) {
				for (int i = 0; i < 3; i++) {
					int x0 = SlotMachineRenderer.WIN_X[i] - SlotMachineRenderer.WIN_W / 2;
					c.rect(x0, SlotMachineRenderer.WIN_TOP, SlotMachineRenderer.WIN_W, SlotMachineRenderer.WIN_H,
						Draw.alpha(0xFFB04CFF, 0.30f * alpha));
				}
			}
			if (e < 150) {
				Draw.glow(c, CX, CY, 80, 0xD8B0FF, 0.9f * (1f - e / 150f) * alpha);
			}
			drawPortalAndDye(c, e, alpha);
		}
		ps.render(c, alpha);
	}

	private void drawPortalAndDye(Canvas c, long e, float alpha) {
		if (e < 150) return;
		float open = Draw.easeOutCubic((e - 150) / 350f);
		int r = Math.round(44 * open);
		if (r > 2) {
			Draw.disc(c, CX, CY, r, Draw.alpha(0xFF07000C, 0.94f * alpha), 1);
			Draw.glow(c, CX, CY, Math.max(4, r - 4), 0x8B2BE0, 0.55f * alpha);
			Draw.ring(c, CX, CY, Math.max(0, r - 3), r, Draw.alpha(RED, alpha));
			Draw.ring(c, CX, CY, Math.max(0, r - 7), Math.max(0, r - 5), Draw.alpha(VIOLET, 0.9f * alpha));
		}
		// light beam
		if (e >= 450 && e <= 950) {
			float bt = (float) Math.sin(Math.PI * Draw.clamp01((e - 450) / 500f));
			c.rect(CX - 5, SlotMachineRenderer.FRAME_TOP - 6, 10, CY - SlotMachineRenderer.FRAME_TOP + 6,
				Draw.alpha(0xFFFFD27A, 0.28f * bt * alpha));
			c.rect(CX - 2, SlotMachineRenderer.FRAME_TOP - 6, 4, CY - SlotMachineRenderer.FRAME_TOP + 6,
				Draw.alpha(0xFFFFFFFF, 0.55f * bt * alpha));
		}
		if (e < 350) return;
		float u = Draw.clamp01((e - 350) / 300f);
		float size = Draw.easeOutBack(u);
		float bob = (float) Math.sin(e / 230.0) * 2.5f;
		float widthFactor = (float) Math.cos((e - 350) * 0.0045);
		Draw.glow(c, CX, Math.round(CY + bob), 40, 0xE02040, 0.9f * u * alpha);
		c.push();
		c.translate(CX, CY + bob);
		c.scale(Math.max(0.01f, size));
		Sprites.drawDye(c, -40, -40, 5, widthFactor, Math.round(255 * alpha));
		c.pop();
	}

	void drawTitles(Canvas c, RollSession s, long now, float alpha, int goldColor) {
		RollSession.Kind kind = s.kind();
		long e = s.sinceLock(now);
		if (e < 0) return;
		if (kind == RollSession.Kind.GOLD) {
			if (e >= 120) {
				float u = Draw.clamp01((e - 120) / 250f);
				float sc = 2.0f * Math.max(0.05f, Draw.easeOutBack(u));
				Draw.centerText(c, "JACKPOT!", CX, 36 + 8 * (2f - sc), sc, Draw.alpha(0xFFFFD84A, alpha * Math.min(1f, u * 3f)), true);
			}
			if (e >= 300) {
				float u = Draw.clamp01((e - 300) / 200f);
				Draw.centerText(c, s.dice.sixCoinsLine(), CX, 58, 1.0f, Draw.alpha(0xFFFFF2B0, alpha * u), true);
			}
		} else if (kind == RollSession.Kind.DYE) {
			if (e >= 500) {
				float u = Draw.clamp01((e - 500) / 250f);
				float sc = 2.0f * Math.max(0.05f, Draw.easeOutBack(u));
				Draw.centerText(c, "ARCHFIEND JACKPOT!", CX, 36 + 8 * (2f - sc), sc, Draw.alpha(0xFFFF4A6A, alpha * Math.min(1f, u * 3f)), true);
			}
			if (e >= 650) {
				float u = Draw.clamp01((e - 650) / 200f);
				Draw.centerText(c, "ARCHFIEND DYE!", CX, 58, 1.3f, Draw.alpha(0xFFD9A8FF, alpha * u), true);
			}
		}
	}

	private static void outline(Canvas c, int x, int y, int w, int h, int t, int argb) {
		c.rect(x, y, w, t, argb);
		c.rect(x, y + h - t, w, t, argb);
		c.rect(x, y + t, t, h - 2 * t, argb);
		c.rect(x + w - t, y + t, t, h - 2 * t, argb);
	}
}
