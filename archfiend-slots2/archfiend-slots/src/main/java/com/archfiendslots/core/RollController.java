package com.archfiendslots.core;

import java.util.function.Consumer;

/**
 * Owns the (at most one) active roll and decides which server messages may affect it.
 * Pure logic, driven by explicit timestamps; the Fabric entrypoint merely forwards events.
 *
 * Rules that protect correctness:
 *  - A result is only accepted while a roll is waiting for it, and exactly once.
 *  - A new roll is refused while one is active, and for a short guard period afterwards
 *    (longer after a timeout), so a late or duplicated message can never be attributed to the next roll.
 *  - Lines that look like player chat are never parsed (nobody can spoof a 7 by typing it).
 *  - Without a confirmed result nothing is ever shown as a win.
 */
public final class RollController {
	public enum Outcome { IGNORED, RESULT, REJECTED, AMBIGUOUS }

	public static final long DUPLICATE_GUARD_MS = 400;
	public static final long LATE_GUARD_MS = 2500;

	private Settings settings;
	private RollResultParser parser;
	private final Consumer<String> log;
	private RollSession current;
	private long nextId = 1;
	private long lockoutUntilMs = Long.MIN_VALUE;
	private boolean lastWasTimeout;

	public RollController(Settings settings, Consumer<String> log) {
		this.log = log == null ? s -> { } : log;
		updateSettings(settings);
	}

	public void updateSettings(Settings s) {
		this.settings = s.sanitize();
		this.parser = new RollResultParser(settings, log);
	}

	public Settings settings() { return settings; }

	/** The active session, or null. Housekeeping runs first so a finished session disappears. */
	public RollSession current(long now) {
		tick(now);
		return current;
	}

	/** A supported item was used. Returns true if a new animation started. */
	public boolean onItemUse(DiceType type, long now) {
		if (!settings.isEnabledFor(type)) {
			log.accept("Dice use ignored: disabled in settings (" + type + ")");
			return false;
		}
		tick(now);
		if (current != null) {
			log.accept("Dice use ignored: roll #" + current.id + " is still active");
			return false;
		}
		if (now < lockoutUntilMs) {
			log.accept("Dice use ignored: guard period, " + (lockoutUntilMs - now) + " ms left");
			return false;
		}
		current = new RollSession(nextId++, type, now, settings.durationSeconds, false);
		log.accept("Roll #" + current.id + " started (" + type + "), waiting for the server message");
		return true;
	}

	/** Starts a clearly-labelled local demo (never touches the real-roll logic and ignores server messages). */
	public boolean startDemo(DiceType type, int result, long now) {
		tick(now);
		if (current != null) return false;
		RollSession s = new RollSession(nextId++, type, now, settings.durationSeconds, true);
		s.resolve(result, now);
		current = s;
		return true;
	}

	/** A game/system/actionbar message arrived. */
	public Outcome onGameMessage(String rawText, long now) {
		tick(now);
		RollSession s = current;
		if (s == null || s.demo || s.isResolved()) return Outcome.IGNORED;

		String text = RollResultParser.normalize(rawText);
		if (text.isEmpty()) return Outcome.IGNORED;
		if (settings.debugLogMessages) log.accept("[pending roll #" + s.id + "] " + text);
		if (parser.isPlayerChat(text)) return Outcome.IGNORED;

		if (parser.isRejection(text)) {
			log.accept("Roll #" + s.id + " rejected by the server: " + text);
			current = null;
			lastWasTimeout = false;
			lockoutUntilMs = now + DUPLICATE_GUARD_MS;
			return Outcome.REJECTED;
		}
		int r = parser.parseResult(text);
		if (r == RollResultParser.AMBIGUOUS) {
			log.accept("Ambiguous roll message ignored: " + text);
			return Outcome.AMBIGUOUS;
		}
		if (r >= 1 && r <= 7 && s.resolve(r, now)) {
			log.accept("Roll #" + s.id + " result " + r + " confirmed from: " + text);
			return Outcome.RESULT;
		}
		return Outcome.IGNORED;
	}

	/** Timeouts and cleanup. Safe to call as often as you like. */
	public void tick(long now) {
		RollSession s = current;
		if (s == null) return;
		if (!s.isResolved() && now - s.startMs >= settings.resultTimeoutMs) {
			s.timeout(now);
			log.accept("No roll result detected within " + settings.resultTimeoutMs + " ms; ending neutrally.");
		}
		// hard safety net: nothing may ever stay on screen or block new rolls for long
		if (now - s.startMs > settings.resultTimeoutMs + 10_000L) {
			log.accept("Roll #" + s.id + " dropped by safety net");
			current = null;
			lockoutUntilMs = Long.MIN_VALUE;
			return;
		}
		if (s.isFinished(now)) {
			log.accept("Roll #" + s.id + " finished" + (s.isTimedOut() ? " (no result detected)" : ""));
			lastWasTimeout = s.isTimedOut();
			lockoutUntilMs = now + (lastWasTimeout ? LATE_GUARD_MS : DUPLICATE_GUARD_MS);
			current = null;
		}
	}

	/** Disconnect, world change, or disabling: drop everything immediately. */
	public void cancelAll() {
		current = null;
		lockoutUntilMs = Long.MIN_VALUE;
		lastWasTimeout = false;
	}

	/** One-line state description for /archfiendslots status. */
	public String describe(long now) {
		RollSession s = current;
		String cur = s == null ? "no active roll"
			: "roll #" + s.id + (s.demo ? " (demo)" : "") + (s.isResolved() ? " result=" + s.result() : " waiting for result");
		long guard = Math.max(0, lockoutUntilMs == Long.MIN_VALUE ? 0 : lockoutUntilMs - now);
		return cur + ", guard " + guard + " ms, enabled=" + settings.enabled
			+ ", standard=" + settings.archfiendDiceEnabled + ", highClass=" + settings.highClassArchfiendDiceEnabled;
	}

	public boolean wasLastTimeout() { return lastWasTimeout; }
}
