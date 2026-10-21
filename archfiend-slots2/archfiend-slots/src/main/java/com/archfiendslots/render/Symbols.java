package com.archfiendslots.render;

/** Symbol ids and the mapping from a confirmed result to the final reel symbols. */
public final class Symbols {
	public static final int COIN = 0;
	public static final int BAR = 1;
	public static final int DIAMOND = 2;
	public static final int RUBY = 3;
	public static final int FLAME = 4;
	public static final int FIEND = 5;
	/** NUM_BASE + n (n = 1..5) are the number symbols. */
	public static final int NUM_BASE = 5;
	public static final int COUNT = 11;

	private Symbols() {}

	/** Final symbols (left, middle, right) for a result; result 0 = not detected. */
	public static int[] finalSymbols(int result) {
		if (result == 6) return new int[] {BAR, BAR, BAR};
		if (result == 7) return new int[] {FIEND, FIEND, FIEND};
		if (result >= 1 && result <= 5) return new int[] {FLAME, NUM_BASE + result, DIAMOND};
		return new int[] {DIAMOND, FLAME, RUBY};
	}

	/** True only for the two winning combinations. */
	public static boolean isTriple(int[] s) {
		return s.length == 3 && s[0] == s[1] && s[1] == s[2];
	}
}
