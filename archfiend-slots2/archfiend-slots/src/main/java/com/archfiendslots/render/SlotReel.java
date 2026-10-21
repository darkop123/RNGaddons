package com.archfiendslots.render;

/**
 * One vertically scrolling reel. The position is a pure, closed-form function of elapsed time
 * (no per-frame integration), so the animation is identical at any frame rate.
 *
 * Positions are measured in symbols. Symbol index i is centred in the window when position == i.
 * The symbols that pass by while spinning are purely cosmetic; the final cell is forced to the
 * symbol dictated by the confirmed result.
 */
public final class SlotReel {
	private final int[] strip;
	private final double speed;
	private final double startPos;

	private boolean stopping;
	private long cmdMs;
	private long durMs;
	private double p0;
	private double p1;
	private int forcedSymbol;

	public SlotReel(int[] strip, double speedSymbolsPerSecond, double startPos) {
		this.strip = strip;
		this.speed = speedSymbolsPerSecond;
		this.startPos = startPos;
	}

	/** Schedules the deceleration. Time values are relative to the start of the roll. */
	public void stop(long commandAtMs, long decelMs, int targetSymbol) {
		this.cmdMs = commandAtMs;
		this.durMs = decelMs;
		this.p0 = startPos + speed * commandAtMs / 1000.0;
		// easeOutCubic starts with slope 3, so this distance matches the spin speed at the start of the stop.
		double nominal = speed * decelMs / 1000.0 / 3.0;
		this.p1 = Math.ceil(p0 + Math.max(2.0, nominal));
		this.forcedSymbol = targetSymbol;
		this.stopping = true;
	}

	public boolean isStopScheduled() {
		return stopping;
	}

	public long settleAtMs() {
		return cmdMs + durMs;
	}

	public boolean isSettled(long tRel) {
		return stopping && tRel >= cmdMs + durMs;
	}

	public boolean isSpinning(long tRel) {
		return !stopping || tRel < cmdMs + durMs;
	}

	public double position(long tRel) {
		if (tRel < 0) tRel = 0;
		if (!stopping || tRel < cmdMs) {
			return startPos + speed * tRel / 1000.0;
		}
		long since = tRel - cmdMs;
		if (since >= durMs) {
			long s = since - durMs;
			// Short damped kick after the reel locks in.
			double wobble = 0.07 * Math.exp(-s / 70.0) * Math.sin(s / 22.0);
			return p1 + wobble;
		}
		double u = since / (double) durMs;
		double inv = 1.0 - u;
		double eased = 1.0 - inv * inv * inv;
		return p0 + (p1 - p0) * eased;
	}

	/** Symbol shown in cell {@code index}. Only the final cell is forced. */
	public int symbolAt(int index) {
		if (stopping && index == (int) p1) return forcedSymbol;
		return strip[Math.floorMod(index, strip.length)];
	}

	/** Symbol currently centred (for tests). */
	public int centredSymbol(long tRel) {
		return symbolAt((int) Math.round(position(tRel)));
	}
}
