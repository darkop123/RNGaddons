package com.archfiendslots.core;

import java.util.Locale;

/** The two supported SkyBlock dice. Pure logic, no Minecraft imports. */
public enum DiceType {
	ARCHFIEND_DICE("ARCHFIEND_DICE", "ARCHFIEND DICE", 15_000_000L, false),
	HIGH_CLASS_ARCHFIEND_DICE("HIGH_CLASS_ARCHFIEND_DICE", "HIGH CLASS ARCHFIEND DICE", 100_000_000L, true);

	/** SkyBlock item id as stored in the item's custom_data ("id" key). */
	public final String skyblockId;
	public final String title;
	/** Coins paid out on a 6 (display only; this mod never grants anything). */
	public final long sixCoins;
	public final boolean highClass;

	DiceType(String skyblockId, String title, long sixCoins, boolean highClass) {
		this.skyblockId = skyblockId;
		this.title = title;
		this.sixCoins = sixCoins;
		this.highClass = highClass;
	}

	/** Exact, case-sensitive match on the SkyBlock id. Anything else (null, other items) returns null. */
	public static DiceType fromSkyBlockId(String id) {
		if (id == null) return null;
		for (DiceType t : values()) {
			if (t.skyblockId.equals(id)) return t;
		}
		return null;
	}

	/** Fallback only: exact (case-insensitive) match on the plain display name. */
	public static DiceType fromDisplayName(String plainName) {
		if (plainName == null) return null;
		String n = plainName.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
		for (DiceType t : values()) {
			if (t.title.equals(n)) return t;
		}
		return null;
	}

	/** "+15,000,000 COINS" / "+100,000,000 COINS". */
	public String sixCoinsLine() {
		return "+" + formatCoins(sixCoins) + " COINS";
	}

	public static String formatCoins(long value) {
		String digits = Long.toString(Math.abs(value));
		StringBuilder sb = new StringBuilder();
		int first = digits.length() % 3;
		if (first > 0) sb.append(digits, 0, first);
		for (int i = first; i < digits.length(); i += 3) {
			if (sb.length() > 0) sb.append(',');
			sb.append(digits, i, i + 3);
		}
		return (value < 0 ? "-" : "") + sb;
	}
}
