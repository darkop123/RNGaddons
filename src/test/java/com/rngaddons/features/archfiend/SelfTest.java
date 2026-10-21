package com.rngaddons.features.archfiend;

import com.rngaddons.features.archfiend.core.DiceType;
import com.rngaddons.features.archfiend.core.RollController;
import com.rngaddons.features.archfiend.core.RollController.Outcome;
import com.rngaddons.features.archfiend.core.RollResultParser;
import com.rngaddons.features.archfiend.core.RollSession;
import com.rngaddons.features.archfiend.core.Settings;
import com.rngaddons.features.archfiend.render.Canvas;
import com.rngaddons.features.archfiend.render.SlotMachineRenderer;
import com.rngaddons.features.archfiend.render.SlotReel;
import com.rngaddons.features.archfiend.render.Symbols;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dependency-free self test of the pure logic (no Minecraft, no JUnit): run with
 *   javac -d out $(find src/main/java/com/rngaddons/features/archfiend/core src/main/java/com/rngaddons/features/archfiend/render -name '*.java') src/test/java/com/rngaddons/features/archfiend/SelfTest.java
 *   java -cp out com.rngaddons.features.archfiend.SelfTest
 * or "gradle selfTest". Exit code 1 on any failure.
 *
 * NOTE: the message wording used below is a hypothesis about Hypixel's text (see README). These tests prove the
 * parser/state machine behave as designed for such lines; they do not prove Hypixel really sends them.
 */
public final class SelfTest {
	private static int passed, failed;

	static void check(boolean ok, String name) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAIL: " + name);
		}
	}

	public static void main(String[] args) {
		diceType();
		parser();
		controllerFlow();
		controllerFromMessage();
		controllerSafety();
		timeline();
		settings();
		rendering();
		System.out.println("\n" + passed + " checks passed, " + failed + " failed");
		if (failed > 0) System.exit(1);
	}

	// ---------------------------------------------------------------- dice detection / amounts

	static void diceType() {
		check(DiceType.fromSkyBlockId("ARCHFIEND_DICE") == DiceType.ARCHFIEND_DICE, "id archfiend");
		check(DiceType.fromSkyBlockId("HIGH_CLASS_ARCHFIEND_DICE") == DiceType.HIGH_CLASS_ARCHFIEND_DICE, "id high class");
		check(DiceType.fromSkyBlockId("SINFUL_DICE") == null, "other dice not matched");
		check(DiceType.fromSkyBlockId("archfiend_dice") == null, "id match is exact/case-sensitive");
		check(DiceType.fromSkyBlockId(null) == null, "null id");
		check(DiceType.fromSkyBlockId("") == null, "empty id");
		check(DiceType.fromDisplayName("  high class  archfiend dice ") == DiceType.HIGH_CLASS_ARCHFIEND_DICE, "name fallback hc");
		check(DiceType.fromDisplayName("Archfiend Dice") == DiceType.ARCHFIEND_DICE, "name fallback std");
		check(DiceType.fromDisplayName("Sinful Dice") == null, "name fallback other");
		check("+15,000,000 COINS".equals(DiceType.ARCHFIEND_DICE.sixCoinsLine()), "std amount: " + DiceType.ARCHFIEND_DICE.sixCoinsLine());
		check("+100,000,000 COINS".equals(DiceType.HIGH_CLASS_ARCHFIEND_DICE.sixCoinsLine()), "hc amount");
		check("1,234".equals(DiceType.formatCoins(1234)) && "999".equals(DiceType.formatCoins(999)), "formatCoins");
	}

	// ---------------------------------------------------------------- parser

	static RollResultParser parser(Settings s) {
		return new RollResultParser(s, w -> { });
	}

	static int parse(RollResultParser p, String raw) {
		String n = RollResultParser.normalize(raw);
		if (p.isPlayerChat(n)) return -99;
		return p.parseResult(n);
	}

	static void parser() {
		RollResultParser p = parser(new Settings());
		for (int n = 1; n <= 7; n++) {
			check(parse(p, "You rolled a " + n + "!") == n, "parse 'You rolled a " + n + "!'");
			check(parse(p, "§aYou rolled a §f" + n + "§a!") == n, "colour codes stripped " + n);
		}
		check(parse(p, "You rolled a Archfiend Dye!") == 7, "dye line => 7");
		check(parse(p, "Your Archfiend Dice has rolled an Archfiend Dye!") == 7, "dye line variant");
		check(parse(p, "Your max health was increased by 40") == 0, "health-only line is NOT a result");
		check(parse(p, "+120 Health") == 0, "health delta alone ignored");
		check(parse(p, "You earned 15,000,000 coins!") == 0, "coin line alone ignored");
		check(parse(p, "You rolled a 4 and then a 6!") == -1, "two numbers => ambiguous");
		check(parse(p, "rolled a 9") == 0, "out of range digit");
		check(parse(p, "You rolled a 44") == 0, "multi digit not accepted");
		check(parse(p, "[MVP+] Steve: I just rolled a 7") == -99, "ranked player chat ignored");
		check(parse(p, "Steve: you rolled a 6!") == -99, "plain player chat ignored");
		check(parse(p, "Guild > Steve: rolled a 7") == -99, "guild chat ignored");
		check(parse(p, "From Steve: rolled a 7") == -99, "private message ignored");
		check(parse(p, "[123] [MVP+] Steve: rolled a 7") == -99, "levelled/ranked chat ignored");
		check(parse(p, "[Bazaar] Sold Archfiend Dye, rolled price 5") == 0, "market words block dye rule");
		check(parse(p, "Dice: You rolled a 5") == 5, "system 'Dice:' prefix is not player chat");
		check(p.isRejection("You do not have enough coins".replace("do not", "don't")), "reject: not enough");
		check(p.isRejection(RollResultParser.normalize("This item is on cooldown")), "reject: cooldown");
		check(!p.isRejection("You rolled a 6"), "result is not a rejection");
		// real Hypixel lines (from the user's recording)
		check(p.isRejection("You do not have enough coins to use the dice!"), "reject: real no-coins line");
		check(!p.isRejection("Your Archfiend Dice rolled a 4! Bonus: +30♥"), "real result is not a rejection");
		for (String[] r : new String[][] {
				{"Your Archfiend Dice rolled a 3! Bonus: -30♥", "3"}, {"Your Archfiend Dice rolled a 2! Bonus: -60♥", "2"},
				{"Your Archfiend Dice rolled a 1! Bonus: -120♥", "1"}, {"Your Archfiend Dice rolled a 5! Bonus: +60♥", "5"},
				{"Your Archfiend Dice rolled a 6! Nice! Bonus: +120♥", "6"}}) {
			check(p.parseResult(RollResultParser.normalize(r[0])) == Integer.parseInt(r[1]), "real line parses: " + r[0]);
			check(!p.isPlayerChat(RollResultParser.normalize(r[0])), "real line is not chat: " + r[0]);
		}

		Settings custom = new Settings();
		custom.extraResultPatterns.add("^DICE RESULT => ([1-7])$");
		custom.extraResultPatterns.add("([unclosed");
		custom.extraRejectPatterns.add("^Nope\\.$");
		List<String> warnings = new ArrayList<>();
		RollResultParser pc = new RollResultParser(custom, warnings::add);
		check(parse(pc, "DICE RESULT => 3") == 3, "custom pattern");
		check(pc.isRejection("Nope."), "custom reject");
		check(warnings.size() == 1, "invalid regex skipped with a warning");
	}

	// ---------------------------------------------------------------- controller

	static RollController newController() {
		return new RollController(new Settings(), s -> { });
	}

	static void controllerFlow() {
		for (int n = 1; n <= 7; n++) {
			RollController c = newController();
			check(c.onItemUse(DiceType.ARCHFIEND_DICE, 1000), "start roll " + n);
			RollSession s = c.current(1000);
			check(s != null && !s.isResolved(), "waiting for result");
			check(c.onGameMessage("You rolled a " + n + "!", 1200) == Outcome.RESULT, "result accepted " + n);
			check(s.isResolved() && s.result() == n, "session holds result " + n);
			RollSession.Kind expect = n == 6 ? RollSession.Kind.GOLD : n == 7 ? RollSession.Kind.DYE : RollSession.Kind.NORMAL;
			check(s.kind() == expect, "kind for " + n);
			// the same / another message must not change or re-trigger anything
			check(c.onGameMessage("You rolled a 7!", 1300) == Outcome.IGNORED, "second message ignored");
			check(s.result() == n, "result unchanged");
		}
		// session ends and disappears
		RollController c = newController();
		c.onItemUse(DiceType.HIGH_CLASS_ARCHFIEND_DICE, 0);
		c.onGameMessage("You rolled a 6!", 100);
		check(c.current(500) != null, "still shown at 0.5 s");
		check(c.current(100 + 5000) == null, "removed after the animation");
	}

	static void controllerFromMessage() {
		// item use not seen: the confirmed server line alone starts the animation
		RollController c = newController();
		check(c.onGameMessage("Your Archfiend Dice rolled a 4! Bonus: +30♥", 1000) == Outcome.RESULT, "message starts roll");
		RollSession s = c.current(1000);
		check(s != null && s.isResolved() && s.result() == 4 && s.kind() == RollSession.Kind.NORMAL, "message roll resolved with 4");
		check(c.onItemUse(DiceType.ARCHFIEND_DICE, 1100) == false, "no second roll while active");
		c = newController();
		check(c.onGameMessage("Your High Class Archfiend Dice rolled a 6! Nice! Bonus: +120♥", 0) == Outcome.RESULT, "hc message");
		check(c.current(0).dice == DiceType.HIGH_CLASS_ARCHFIEND_DICE && c.current(0).kind() == RollSession.Kind.GOLD, "hc gold");
		// things that must NOT start a roll
		c = newController();
		check(c.onGameMessage("You rolled a 6!", 0) == Outcome.IGNORED, "no dice name => nothing");
		check(c.onGameMessage("[MVP+] Bob: Your Archfiend Dice rolled a 7!", 0) == Outcome.IGNORED, "player chat spoof ignored");
		check(c.onGameMessage("Your Archfiend Dice rolled a 4 and a 6!", 0) == Outcome.IGNORED, "ambiguous ignored");
		check(c.onGameMessage("You do not have enough coins to use the dice!", 0) == Outcome.IGNORED, "rejection ignored");
		check(c.current(0) == null, "nothing started");
		// guard after a finished roll
		c = newController();
		c.onGameMessage("Your Archfiend Dice rolled a 2! Bonus: -60♥", 0);
		long end = c.current(0).totalMs() + 50;
		c.tick(end);
		check(c.onGameMessage("Your Archfiend Dice rolled a 7!", end + 100) == Outcome.IGNORED, "guard blocks duplicate line");
	}

	static void controllerSafety() {
		// unresolved => timeout => neutral, never a win
		RollController c = newController();
		c.onItemUse(DiceType.ARCHFIEND_DICE, 0);
		RollSession s = c.current(0);
		check(c.current(4999) == s && !s.isResolved(), "still waiting before timeout");
		c.tick(5000);
		check(s.isResolved() && s.isTimedOut() && s.kind() == RollSession.Kind.UNKNOWN, "timeout => UNKNOWN");
		check(s.result() == 0, "no result invented");
		check(!Symbols.isTriple(Symbols.finalSymbols(0)), "timeout reels are not a winning combination");
		// late result after timeout must not alter it nor start a new animation
		check(c.onGameMessage("You rolled a 7!", 5200) == Outcome.IGNORED, "late result ignored after timeout");
		check(s.kind() == RollSession.Kind.UNKNOWN, "still UNKNOWN");
		long end = 5000 + s.totalMs() - 0;
		c.tick(end + 10);
		check(c.current(end + 10) == null, "timed-out session ends");
		// a new use right after a timeout is blocked for the late guard (so the stale line can't be mis-assigned)
		check(!c.onItemUse(DiceType.ARCHFIEND_DICE, end + 100), "new roll blocked during late guard");
		check(!c.onGameMessage("You rolled a 6!", end + 200).equals(Outcome.RESULT), "no stale result without a roll");
		check(c.onItemUse(DiceType.ARCHFIEND_DICE, end + 10 + RollController.LATE_GUARD_MS + 5), "new roll allowed after the guard");

		// use while active is ignored (no second animation, no re-assignment)
		c = newController();
		check(c.onItemUse(DiceType.ARCHFIEND_DICE, 0), "first use");
		check(!c.onItemUse(DiceType.ARCHFIEND_DICE, 50), "second use while active ignored");
		check(!c.onItemUse(DiceType.HIGH_CLASS_ARCHFIEND_DICE, 60), "other dice while active ignored");

		// rejected roll aborts without a win
		c = newController();
		c.onItemUse(DiceType.ARCHFIEND_DICE, 0);
		check(c.onGameMessage("You don't have enough coins!", 100) == Outcome.REJECTED, "rejection detected");
		check(c.current(100) == null, "rejected roll removes the overlay");
		check(!c.onItemUse(DiceType.ARCHFIEND_DICE, 200), "duplicate guard right after rejection");
		check(c.onItemUse(DiceType.ARCHFIEND_DICE, 100 + RollController.DUPLICATE_GUARD_MS + 1), "usable again soon after");

		// no roll => messages are ignored
		c = newController();
		check(c.onGameMessage("You rolled a 7!", 0) == Outcome.IGNORED, "message without roll ignored");
		check(c.current(0) == null, "nothing starts from a message alone");

		// spoofing by player chat
		c = newController();
		c.onItemUse(DiceType.ARCHFIEND_DICE, 0);
		check(c.onGameMessage("[MVP+] Steve: You rolled a 7!", 50) == Outcome.IGNORED, "chat cannot trigger a jackpot");
		check(!c.current(60).isResolved(), "still pending after spoof");

		// ambiguous message keeps waiting
		check(c.onGameMessage("rolled a 3 rolled a 4", 80) == Outcome.AMBIGUOUS, "ambiguous reported");
		check(!c.current(90).isResolved(), "ambiguous does not resolve");

		// demo mode is isolated from server messages
		c = newController();
		check(c.startDemo(DiceType.ARCHFIEND_DICE, 7, 0), "demo starts");
		RollSession d = c.current(0);
		check(d.demo && d.kind() == RollSession.Kind.DYE, "demo shows the requested animation");
		check(c.onGameMessage("You rolled a 2!", 10) == Outcome.IGNORED, "demo ignores real messages");
		check(!c.onItemUse(DiceType.ARCHFIEND_DICE, 20), "real use blocked while demo runs");

		// disabled dice / feature
		Settings st = new Settings();
		st.highClassArchfiendDiceEnabled = false;
		c = new RollController(st, x -> { });
		check(!c.onItemUse(DiceType.HIGH_CLASS_ARCHFIEND_DICE, 0), "high class disabled");
		check(c.onItemUse(DiceType.ARCHFIEND_DICE, 0), "normal still enabled");
		st = new Settings();
		st.enabled = false;
		c = new RollController(st, x -> { });
		check(!c.onItemUse(DiceType.ARCHFIEND_DICE, 0), "mod disabled");

		// disconnect
		c = newController();
		c.onItemUse(DiceType.ARCHFIEND_DICE, 0);
		c.cancelAll();
		check(c.current(10) == null, "cancelAll removes the roll");
		check(c.onItemUse(DiceType.ARCHFIEND_DICE, 20), "usable again after cancel");
	}

	// ---------------------------------------------------------------- timeline / reels

	static void timeline() {
		for (DiceType dice : DiceType.values()) {
			for (int r = 1; r <= 7; r++) {
				RollSession s = new RollSession(r, dice, 0, 3.0, false);
				s.resolve(r, 100);
				int[] fin = Symbols.finalSymbols(r);
				long lock = s.totalMs();
				boolean allMatch = true;
				for (int i = 0; i < 3; i++) {
					SlotReel reel = s.reel(i);
					long settled = reel.settleAtMs();
					allMatch &= reel.isSettled(settled + 1);
					allMatch &= reel.centredSymbol(settled + 1000) == fin[i];
					allMatch &= Math.abs(reel.position(settled + 1000) - Math.rint(reel.position(settled + 1000))) < 1e-3;
				}
				check(allMatch, "final symbols match result " + r);
				check(Symbols.isTriple(fin) == (r == 6 || r == 7), "only 6 and 7 are triples (result " + r + ")");
				long max = s.totalMs();
				if (r <= 5) check(max == 3000, "normal result takes 3.0 s, was " + max);
				// jackpots: last reel at 2.3 s + default celebration 2.5 s
				if (r >= 6) check(Math.abs(max - 4800) <= 1, "jackpot 2.3 s + 2.5 s celebration, was " + max);
				check(s.sinceLock(0) < 0, "not locked at start");
			}
		}

		// reel timing at the default 3.0 s: 1.2 / 1.8 / 2.3
		RollSession s = new RollSession(1, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		s.resolve(4, 50);
		check(s.reel(0).settleAtMs() == 1200, "reel 1 settles at 1.2 s: " + s.reel(0).settleAtMs());
		check(s.reel(1).settleAtMs() == 1800, "reel 2 settles at 1.8 s: " + s.reel(1).settleAtMs());
		check(Math.abs(s.reel(2).settleAtMs() - 2300) <= 1, "reel 3 settles at 2.3 s: " + s.reel(2).settleAtMs());

		// reels keep spinning (not stopped) until the result is known
		s = new RollSession(2, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		check(s.anyReelSpinning(10_000), "spins forever without result");
		check(!s.isResolved() && s.sinceLock(5000) == Long.MIN_VALUE, "unresolved has no lock time");

		// a late result shifts the stops back and still ends on the right symbols
		s = new RollSession(3, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		s.resolve(7, 4000);
		check(s.reel(0).settleAtMs() >= 4000 + RollSession.DECEL_MS, "late result: reel 1 stops after it arrived");
		check(s.reel(2).centredSymbol(s.reel(2).settleAtMs() + 500) == Symbols.FIEND, "late result: reel 3 shows the dye symbol");
		check(s.totalMs() > 4000 && s.isFinished(s.totalMs() + 1), "late result: still finishes");

		// resolve only once; invalid values rejected
		s = new RollSession(4, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		check(!s.resolve(0, 10) && !s.resolve(8, 10), "invalid results rejected");
		check(s.resolve(5, 10) && !s.resolve(7, 20) && !s.timeout(30), "second resolution refused");
		check(s.result() == 5, "first result kept");

		// frame-rate independence: position is a pure function of time
		s = new RollSession(5, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		s.resolve(6, 100);
		double a = s.reel(1).position(1337);
		double b = 0;
		for (int i = 0; i < 100; i++) b = s.reel(1).position(1337);
		check(a == b, "position(t) deterministic");

		// events: each exactly once, in order, nothing before they are due
		s = new RollSession(6, DiceType.HIGH_CLASS_ARCHFIEND_DICE, 0, 3.0, false);
		s.resolve(6, 100);
		List<Integer> events = new ArrayList<>();
		for (long t = 0; t <= 4000; t += 16) {
			int ev;
			while ((ev = s.pollEvent(t)) != RollSession.EV_NONE) events.add(ev);
		}
		check(events.get(0) == RollSession.EV_START, "start event first");
		check(count(events, RollSession.EV_STOP_1) == 1 && count(events, RollSession.EV_STOP_2) == 1
			&& count(events, RollSession.EV_STOP_3) == 1, "stop events once each");
		check(count(events, RollSession.EV_WIN_GOLD) == 1 && count(events, RollSession.EV_WIN_DYE) == 0, "gold win event once, no dye event");
		RollSession n = new RollSession(7, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		n.resolve(3, 100);
		boolean winEvent = false;
		for (long t = 0; t <= 4000; t += 16) {
			int ev;
			while ((ev = n.pollEvent(t)) != RollSession.EV_NONE) winEvent |= (ev == RollSession.EV_WIN_GOLD || ev == RollSession.EV_WIN_DYE);
		}
		check(!winEvent, "no win event for a normal result");

		// duration and celebration settings are clamped
		RollSession big = new RollSession(8, DiceType.ARCHFIEND_DICE, 0, 99.0, 99.0, false);
		big.resolve(7, 10);
		check(Math.abs(big.reel(2).settleAtMs() - 3833) <= 2, "huge duration clamped to 5 s: " + big.reel(2).settleAtMs());
		check(big.totalMs() <= 3834 + 6000, "huge celebration clamped to 6 s: " + big.totalMs());
		RollSession tiny = new RollSession(9, DiceType.ARCHFIEND_DICE, 0, 0.1, false);
		tiny.resolve(2, 10);
		check(tiny.totalMs() >= 1500 - 10, "tiny duration clamped to 1.5 s: " + tiny.totalMs());
		// the duration setting changes when the reels stop: 2 s vs 3 s
		RollSession two = new RollSession(11, DiceType.ARCHFIEND_DICE, 0, 2.0, false);
		two.resolve(4, 10);
		check(Math.abs(two.reel(2).settleAtMs() - 1533) <= 2, "2 s setting: last reel at ~1.53 s: " + two.reel(2).settleAtMs());
		// celebration length setting
		RollSession longDye = new RollSession(12, DiceType.ARCHFIEND_DICE, 0, 3.0, 4.0, false);
		longDye.resolve(7, 10);
		check(Math.abs(longDye.totalMs() - 6300) <= 1, "dye with 4 s celebration: " + longDye.totalMs());
		RollSession shortDye = new RollSession(13, DiceType.ARCHFIEND_DICE, 0, 3.0, 1.0, false);
		shortDye.resolve(7, 10);
		check(shortDye.totalMs() - shortDye.reel(2).settleAtMs() >= 1000, "dye keeps >= 1 s for its titles");
		RollSession normalLong = new RollSession(14, DiceType.ARCHFIEND_DICE, 0, 3.0, 6.0, false);
		normalLong.resolve(3, 10);
		check(normalLong.totalMs() == 3000, "celebration setting does not lengthen normal results");

		// every strip contains all symbols (the forced final cell is the only thing that matters, but be sure)
		Set<Integer> seen = new HashSet<>();
		SlotReel rl = new RollSession(10, DiceType.ARCHFIEND_DICE, 0, 3.0, false).reel(0);
		for (int i = 0; i < Symbols.COUNT; i++) seen.add(rl.symbolAt(i));
		check(seen.size() == Symbols.COUNT, "strip is a permutation of all symbols");
	}

	static int count(List<Integer> l, int v) {
		int c = 0;
		for (int x : l) if (x == v) c++;
		return c;
	}

	// ---------------------------------------------------------------- settings

	static void settings() {
		Settings s = new Settings();
		s.durationSeconds = 99;
		s.overlayScale = -3;
		s.soundVolume = 7;
		s.resultTimeoutMs = 1;
		s.extraResultPatterns = null;
		s.extraRejectPatterns = null;
		s.sanitize();
		check(s.durationSeconds == 5.0 && s.overlayScale == 0.5 && s.soundVolume == 1.0 && s.resultTimeoutMs == 2000, "sanitize clamps");
		s.jackpotSeconds = 0.1;
		s.sanitize();
		check(s.jackpotSeconds == 1.0, "jackpot length clamped up");
		s.jackpotSeconds = Double.NaN;
		s.sanitize();
		check(s.jackpotSeconds == 2.5, "NaN jackpot length repaired");
		check(s.extraResultPatterns != null && s.extraRejectPatterns != null, "sanitize repairs null lists");
		s.durationSeconds = Double.NaN;
		s.sanitize();
		check(s.durationSeconds == 3.0, "NaN duration repaired");
		check(new Settings().isEnabledFor(DiceType.ARCHFIEND_DICE) && new Settings().isEnabledFor(DiceType.HIGH_CLASS_ARCHFIEND_DICE), "defaults enable both dice");
		check(!new Settings().isEnabledFor(null), "null dice");
	}

	// ---------------------------------------------------------------- rendering (headless)

	/** Canvas that records everything; used to prove what is (not) drawn and that the pose stack stays balanced. */
	static final class Recorder implements Canvas {
		int depth, maxDepth, rects, minDepth;
		final List<String> texts = new ArrayList<>();
		@Override public int width() { return 480; }
		@Override public int height() { return 270; }
		@Override public void push() { depth++; maxDepth = Math.max(maxDepth, depth); }
		@Override public void pop() { depth--; minDepth = Math.min(minDepth, depth); }
		@Override public void translate(float x, float y) { }
		@Override public void scale(float s) { }
		@Override public void rect(int x, int y, int w, int h, int argb) { rects++; }
		@Override public void text(String s, int x, int y, int argb, boolean shadow) { texts.add(s); }
		@Override public int textWidth(String s) { return s.length() * 6; }
	}

	static void rendering() {
		Settings cfg = new Settings().sanitize();
		for (DiceType dice : DiceType.values()) {
			for (int r = 0; r <= 7; r++) {
				RollSession s = new RollSession(r + 1, dice, 0, 3.0, false);
				if (r == 0) s.timeout(100); else s.resolve(r, 100);
				SlotMachineRenderer sm = new SlotMachineRenderer();
				Set<String> allTexts = new HashSet<>();
				int maxRects = 0;
				boolean balanced = true;
				for (long t = 0; t <= s.totalMs() + 100; t += 16) {
					Recorder rec = new Recorder();
					sm.render(rec, s, t, cfg);
					allTexts.addAll(rec.texts);
					maxRects = Math.max(maxRects, rec.rects);
					balanced &= rec.depth == 0 && rec.minDepth >= 0;
				}
				String label = dice + " result " + r;
				check(balanced, "push/pop balanced: " + label);
				check(maxRects < 6000, "bounded draw calls (" + maxRects + "): " + label);
				boolean sawJackpot = allTexts.contains("JACKPOT!");
				boolean sawArch = allTexts.contains("ARCHFIEND JACKPOT!") || allTexts.contains("ARCHFIEND DYE!");
				boolean sawCoins = allTexts.contains(dice.sixCoinsLine());
				check(sawJackpot == (r == 6), "gold text only for a 6: " + label);
				check(sawCoins == (r == 6), "correct coin amount only for a 6: " + label);
				check(sawArch == (r == 7), "dye text only for a 7: " + label);
				if (r >= 1 && r <= 5) check(allTexts.contains("RESULT: " + r), "shows the real number: " + label);
				if (r == 0) check(allTexts.contains("RESULT NOT DETECTED"), "neutral message when undetected");
				// other dice' amount must never appear
				for (DiceType other : DiceType.values()) {
					if (other != dice) check(!allTexts.contains(other.sixCoinsLine()), "wrong amount never drawn: " + label);
				}
			}
		}
		// nothing is drawn when no session / after the end
		SlotMachineRenderer sm = new SlotMachineRenderer();
		Recorder rec = new Recorder();
		sm.render(rec, null, 0, cfg);
		check(rec.rects == 0 && rec.depth == 0, "no session => nothing drawn");
		RollSession done = new RollSession(1, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		done.resolve(7, 10);
		rec = new Recorder();
		sm.render(rec, done, done.totalMs() + 500, cfg);
		check(rec.rects == 0, "after the end nothing is drawn (alpha 0)");

		// demo is visibly labelled
		RollSession demo = new RollSession(2, DiceType.ARCHFIEND_DICE, 0, 3.0, true);
		demo.resolve(6, 0);
		rec = new Recorder();
		sm.render(rec, demo, 1500, cfg);
		check(rec.texts.contains("DEMO - NOT A REAL ROLL"), "demo label shown");

		// settings toggles: no particles / no shake still render fine
		Settings quiet = new Settings();
		quiet.particlesEnabled = false;
		quiet.screenShakeEnabled = false;
		RollSession q = new RollSession(3, DiceType.ARCHFIEND_DICE, 0, 3.0, false);
		q.resolve(7, 10);
		for (long t = 0; t < 3600; t += 16) {
			rec = new Recorder();
			sm.render(rec, q, t, quiet);
			if (rec.depth != 0) check(false, "balanced with effects off");
		}
		check(true, "renders with particles/shake disabled");
	}
}
