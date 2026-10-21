package com.archfiendslots.render;

/**
 * Fixed-size particle pool (no allocation per frame, hard cap, nothing persists after the roll ends:
 * the renderer clears it whenever no session is active). Purely cosmetic; uses its own tiny PRNG and
 * never influences any result.
 */
public final class ParticleSystem {
	public static final int CAPACITY = 96;

	private final float[] x = new float[CAPACITY];
	private final float[] y = new float[CAPACITY];
	private final float[] vx = new float[CAPACITY];
	private final float[] vy = new float[CAPACITY];
	private final float[] life = new float[CAPACITY];
	private final float[] maxLife = new float[CAPACITY];
	private final float[] size = new float[CAPACITY];
	private final float[] gravity = new float[CAPACITY];
	private final int[] color = new int[CAPACITY];
	private int count;
	private int seed = 0x2545F491;

	public void clear() {
		count = 0;
	}

	public int count() {
		return count;
	}

	private float rnd() {
		int s = seed;
		s ^= s << 13;
		s ^= s >>> 17;
		s ^= s << 5;
		seed = s;
		return (s & 0xFFFFFF) / (float) 0x1000000;
	}

	public void emit(float px, float py, float pvx, float pvy, float lifeSec, float sz, float grav, int argb) {
		if (count >= CAPACITY) return;
		int i = count++;
		x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
		life[i] = lifeSec; maxLife[i] = lifeSec; size[i] = sz; gravity[i] = grav; color[i] = argb;
	}

	/** Radial burst from a point. */
	public void burst(float cx, float cy, int n, float speedMin, float speedMax, float lifeSec, float sz, float grav,
	                  int colorA, int colorB) {
		for (int k = 0; k < n; k++) {
			double a = rnd() * Math.PI * 2.0;
			float sp = speedMin + rnd() * (speedMax - speedMin);
			emit(cx, cy, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
				lifeSec * (0.7f + 0.6f * rnd()), sz, grav, rnd() < 0.5f ? colorA : colorB);
		}
	}

	/** One rising ember on a circle (used for the orbiting flames). */
	public void ember(float cx, float cy, float radius, float angle, float lifeSec, int argb) {
		float ex = cx + (float) Math.cos(angle) * radius;
		float ey = cy + (float) Math.sin(angle) * radius;
		float tx = (float) -Math.sin(angle) * 22f;
		float ty = (float) Math.cos(angle) * 22f;
		emit(ex, ey, tx, ty - 14f - rnd() * 10f, lifeSec, 2f, -4f, argb);
	}

	public float random() {
		return rnd();
	}

	/** dt in seconds (callers clamp it). */
	public void update(float dt) {
		int i = 0;
		while (i < count) {
			life[i] -= dt;
			if (life[i] <= 0f) {
				int last = --count;
				x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last];
				life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last];
				gravity[i] = gravity[last]; color[i] = color[last];
				continue;
			}
			vy[i] += gravity[i] * dt;
			x[i] += vx[i] * dt;
			y[i] += vy[i] * dt;
			i++;
		}
	}

	public void render(Canvas c, float globalAlpha) {
		for (int i = 0; i < count; i++) {
			float t = life[i] / maxLife[i];
			float a = Math.min(1f, t * 2.2f) * globalAlpha;
			int alpha = (int) (a * (color[i] >>> 24));
			if (alpha <= 2) continue;
			int s = Math.max(1, Math.round(size[i]));
			c.rect(Math.round(x[i]) - s / 2, Math.round(y[i]) - s / 2, s, s, (alpha << 24) | (color[i] & 0xFFFFFF));
		}
	}
}
