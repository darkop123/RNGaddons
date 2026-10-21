package com.rngaddons.features.archfiend.core;

import com.rngaddons.features.archfiend.render.SlotReel;
import com.rngaddons.features.archfiend.render.Symbols;

/**
 * One animation instance: the timeline of a single roll. Pure logic with explicit millisecond timestamps.
 *
 * The reels spin from t = 0. They only get a stop command once a result is known (confirmed from the server,
 * or "unknown" after the timeout), so the final symbols always match the confirmed result.
 *
 * Timeline at the default 3.0 s (relative to the start):
 *   0.0-0.2  fade/scale in            1.2 / 1.8 / 2.3  reel 1 / 2 / 3 settle
 *   2.3-3.0  result presentation      (+0.3 s gold, +0.5 s dye jackpot)
 * If the result is late, the stops shift back; the presentation is appended after the last reel.
 */
public final class RollSession {
	public enum Kind { NORMAL, GOLD, DYE, UNKNOWN }

	public static final double MIN_DURATION_S = 1.5;
	public static final double MAX_DURATION_S = 5.0;
	public static final double MIN_JACKPOT_S = 1.0;
	public static final double MAX_JACKPOT_S = 6.0;
	public static final double DEFAULT_JACKPOT_S = 2.5;
	public static final long DECEL_MS = 450;
	public static final long INTRO_MS = 200;
	public static final long FADE_OUT_MS = 250;
	private static final double[] STOP_FRACTION = {0.40, 0.60, 0.7667};
	private static final double[] REEL_SPEED = {19.0, 22.0, 25.0};
	private static final long MIN_LEAD_MS = 40;
	private static final long LATE_STAGGER_MS = 260;

	// event codes returned by pollEvent
	public static final int EV_NONE = -1;
	public static final int EV_START = 0;
	public static final int EV_TICK = 1;
	public static final int EV_STOP_1 = 2;
	public static final int EV_STOP_2 = 3;
	public static final int EV_STOP_3 = 4;
	public static final int EV_WIN_GOLD = 5;
	public static final int EV_WIN_DYE = 6;

	public final long id;
	public final DiceType dice;
	public final long startMs;
	public final boolean demo;
	private final long baseMs;

	private final SlotReel[] reels = new SlotReel[3];
	private int result;
	private boolean resolved;
	private boolean timedOut;
	private long resolvedAtMs;
	private long lockRel;
	private long endRel;

	// event queue
	private final long[] evTime = new long[6];
	private final int[] evCode = new int[6];
	private final long jackpotMs;
	private int evCount;
	private int evNext;
	private boolean startEmitted;
	private long lastTickRel = Long.MIN_VALUE;

	public RollSession(long id, DiceType dice, long startMs, double durationSeconds, boolean demo) {
		this(id, dice, startMs, durationSeconds, DEFAULT_JACKPOT_S, demo);
	}

	public RollSession(long id, DiceType dice, long startMs, double durationSeconds, double jackpotSeconds, boolean demo) {
		this.id = id;
		this.dice = dice;
		this.startMs = startMs;
		this.demo = demo;
		this.baseMs = Math.round(clamp(durationSeconds, MIN_DURATION_S, MAX_DURATION_S, 3.0) * 1000.0);
		this.jackpotMs = Math.round(clamp(jackpotSeconds, MIN_JACKPOT_S, MAX_JACKPOT_S, DEFAULT_JACKPOT_S) * 1000.0);
		// Cosmetic strips: different symbol order per reel, every symbol present.
		int[] steps = {3, 4, 7};
		for (int r = 0; r < 3; r++) {
			int[] strip = new int[Symbols.COUNT];
			for (int j = 0; j < Symbols.COUNT; j++) strip[j] = (j * steps[r] + (int) (id * 2 + r * 5)) % Symbols.COUNT;
			reels[r] = new SlotReel(strip, REEL_SPEED[r], (id * 3 + r * 4) % Symbols.COUNT);
		}
	}

	// ------------------------------------------------------------------ resolution

	/** Accepts the confirmed result. Returns false if this session already has one (a result is only used once). */
	public boolean resolve(int value, long nowMs) {
		if (resolved || value < 1 || value > 7) return false;
		result = value;
		finishResolution(nowMs);
		return true;
	}

	/** Ends the wait without a result: neutral symbols, never a win. */
	public boolean timeout(long nowMs) {
		if (resolved) return false;
		result = 0;
		timedOut = true;
		finishResolution(nowMs);
		return true;
	}

	private void finishResolution(long nowMs) {
		resolved = true;
		resolvedAtMs = nowMs;
		long tr = Math.max(0, nowMs - startMs);
		int[] target = Symbols.finalSymbols(result);
		long prevCmd = Long.MIN_VALUE;
		for (int i = 0; i < 3; i++) {
			long idealSettle = Math.round(STOP_FRACTION[i] * baseMs);
			long cmd = Math.max(idealSettle - DECEL_MS, tr + MIN_LEAD_MS + LATE_STAGGER_MS * i);
			if (prevCmd != Long.MIN_VALUE) cmd = Math.max(cmd, prevCmd + 120);
			prevCmd = cmd;
			reels[i].stop(cmd, DECEL_MS, target[i]);
		}
		lockRel = reels[2].settleAtMs();
		long present = Math.max(500, baseMs - Math.round(STOP_FRACTION[2] * baseMs));
		switch (kind()) {
			case GOLD -> present = Math.max(present, jackpotMs);
			case DYE -> present = Math.max(present, Math.max(1000, jackpotMs)); // titles need ~0.85 s
			case UNKNOWN -> present = Math.max(present, 900);
			default -> { }
		}
		endRel = lockRel + present;

		evCount = 0;
		evNext = 0;
		addEvent(reels[0].settleAtMs(), EV_STOP_1);
		addEvent(reels[1].settleAtMs(), EV_STOP_2);
		addEvent(lockRel, EV_STOP_3);
		if (kind() == Kind.GOLD) addEvent(lockRel, EV_WIN_GOLD);
		if (kind() == Kind.DYE) addEvent(lockRel, EV_WIN_DYE);
	}

	private void addEvent(long rel, int code) {
		evTime[evCount] = rel;
		evCode[evCount] = code;
		evCount++;
	}

	// ------------------------------------------------------------------ queries

	public boolean isResolved() { return resolved; }
	public boolean isTimedOut() { return timedOut; }
	public int result() { return result; }
	public long resolvedAtMs() { return resolvedAtMs; }
	public SlotReel reel(int i) { return reels[i]; }
	public long baseDurationMs() { return baseMs; }

	public Kind kind() {
		if (!resolved || timedOut) return Kind.UNKNOWN;
		if (result == 6) return Kind.GOLD;
		if (result == 7) return Kind.DYE;
		return Kind.NORMAL;
	}

	public long elapsed(long nowMs) { return Math.max(0, nowMs - startMs); }

	/** Total length of the roll in ms (valid once resolved). */
	public long totalMs() { return resolved ? endRel : -1; }

	public boolean isFinished(long nowMs) {
		return resolved && elapsed(nowMs) >= endRel;
	}

	/** Milliseconds since the last reel locked in; negative before that; Long.MIN_VALUE while unresolved. */
	public long sinceLock(long nowMs) {
		if (!resolved) return Long.MIN_VALUE;
		return elapsed(nowMs) - lockRel;
	}

	/** Overall overlay opacity 0..1 (fade in, fade out). */
	public float alpha(long nowMs) {
		long t = elapsed(nowMs);
		float a = Math.min(1f, t / (float) INTRO_MS);
		if (resolved) {
			long left = endRel - t;
			if (left < FADE_OUT_MS) a = Math.min(a, Math.max(0f, left / (float) FADE_OUT_MS));
		}
		return a;
	}

	/** Scale 0.85 -> 1.0 with a small overshoot during the intro. */
	public float introScale(long nowMs) {
		float u = Math.min(1f, elapsed(nowMs) / (float) INTRO_MS);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float v = u - 1f;
		float e = 1f + c3 * v * v * v + c1 * v * v;
		return 0.85f + 0.15f * e;
	}

	public boolean anyReelSpinning(long nowMs) {
		long t = elapsed(nowMs);
		for (SlotReel r : reels) {
			if (r.isSpinning(t)) return true;
		}
		return false;
	}

	/**
	 * Returns the next due event (once each), or EV_NONE. Call in a loop until EV_NONE.
	 * Spin ticks are emitted while any reel still spins.
	 */
	public int pollEvent(long nowMs) {
		long t = elapsed(nowMs);
		if (!startEmitted) {
			startEmitted = true;
			return EV_START;
		}
		if (evNext < evCount && t >= evTime[evNext]) {
			return evCode[evNext++];
		}
		boolean firstReelSpinning = reels[0].isSpinning(t);
		if (firstReelSpinning && t - lastTickRel >= 170) {
			lastTickRel = t;
			return EV_TICK;
		}
		return EV_NONE;
	}

	private static double clamp(double v, double lo, double hi, double fallback) {
		if (v != v) return fallback;
		return Math.max(lo, Math.min(hi, v));
	}
}
