package com.archfiendslots.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Extracts the server-decided roll result (1-7) from a plain-text game message.
 *
 * IMPORTANT: the exact Hypixel wording for every roll result is NOT verified (the official wiki does not
 * document it and public mods list the 6/7 wording as unconfirmed). The defaults below are conservative
 * guesses; they only ever accept a line that
 *   - is not player/guild/party/private chat,
 *   - contains an explicit "rolled"-style phrase followed by a single digit 1-7, and
 *   - does not contain two different candidate numbers.
 * Use /archfiendslots capture to log the real lines and add exact patterns via "extraResultPatterns".
 */
public final class RollResultParser {
	public static final int NONE = 0;
	public static final int AMBIGUOUS = -1;

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private static final String[] DEFAULT_RESULT = {
		"\\brolled\\s+(?:a\\s+|an\\s+|the\\s+number\\s+)?([1-7])\\b",
		"\\bdice\\s+(?:roll\\s+)?(?:result|landed\\s+on|rolled)\\s*(?:is|was|:)?\\s*(?:a\\s+)?([1-7])\\b",
		"\\byou\\s+(?:got|landed\\s+on)\\s+(?:a\\s+)?([1-7])\\b.*\\b(?:dice|archfiend)\\b",
	};

	/** Only used for the 7: "rolled ... Archfiend Dye" with a roll verb and without market words. */
	private static final Pattern DYE_ROLL = Pattern.compile("\\brolled\\b.*\\barchfiend\\s+dye\\b", FLAGS);
	private static final Pattern MARKET_WORDS = Pattern.compile(
		"\\b(?:auction|bazaar|bid|bids|bought|buy|sold|sell|selling|trade|trading|offer|bin|lowest|price|for sale)\\b", FLAGS);

	private static final Pattern STANDALONE_DIGIT = Pattern.compile("(?<![\\d.,])[1-7](?![\\d.,])");

	private static final String[] DEFAULT_REJECT = {
		"\\bnot\\s+enough\\s+coins\\b",
		"\\bdon'?t\\s+have\\s+enough\\b",
		"\\b(?:do\\s+not|does\\s+not|did\\s+not)\\s+have\\s+enough\\b",
		"\\bnot\\s+have\\s+enough\\b",		"\\b(?:can'?t|cannot)\\s+afford\\b",
		"\\bon\\s+cooldown\\b",
		"\\bcooldown\\b.*\\b(?:wait|remaining|seconds)\\b",
		"\\bcan(?:'|no)?t\\s+use\\s+this\\b",
	};

	/** "[MVP+] Name: text", "Name: text", "Guild > Name: text", "From Name: text", ... */
	private static final Pattern PLAYER_CHAT = Pattern.compile(
		"^(?:(?:guild|party|co-op|officer|from|to)\\b.*?:|(?:\\[[^\\]]{1,24}\\]\\s*)+[A-Za-z0-9_]{1,16}\\s?:|[A-Za-z0-9_]{1,16}\\s?:)\\s", FLAGS);
	private static final Pattern CHAT_NAME_EXCEPTIONS = Pattern.compile(
		"^(?:archfiend(?:\\s+dice)?|high\\s+class\\s+archfiend\\s+dice|dice|result|roll|rolled|error|notice|warning)\\s?:\\s", FLAGS);

	private final List<Pattern> result = new ArrayList<>();
	private final List<Pattern> reject = new ArrayList<>();

	public RollResultParser(Settings settings, Consumer<String> warn) {
		for (String p : DEFAULT_RESULT) result.add(Pattern.compile(p, FLAGS));
		for (String p : DEFAULT_REJECT) reject.add(Pattern.compile(p, FLAGS));
		if (settings != null) {
			compileInto(settings.extraResultPatterns, result, warn);
			compileInto(settings.extraRejectPatterns, reject, warn);
		}
	}

	private static void compileInto(List<String> src, List<Pattern> dst, Consumer<String> warn) {
		if (src == null) return;
		for (String p : src) {
			if (p == null || p.isBlank()) continue;
			try {
				dst.add(Pattern.compile(p, FLAGS));
			} catch (PatternSyntaxException e) {
				if (warn != null) warn.accept("Ignoring invalid regex '" + p + "': " + e.getDescription());
			}
		}
	}

	/** Strips legacy colour codes (section sign + char), control characters and collapses whitespace. */
	public static String normalize(String raw) {
		if (raw == null) return "";
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '§') { i++; continue; }
			if (c < 0x20 || c == 0x7F) { sb.append(' '); continue; }
			sb.append(c);
		}
		return sb.toString().replaceAll("\\s+", " ").trim();
	}

	public boolean isPlayerChat(String normalized) {
		if (normalized.isEmpty()) return false;
		if (CHAT_NAME_EXCEPTIONS.matcher(normalized).find()) return false;
		return PLAYER_CHAT.matcher(normalized).find();
	}

	public boolean isRejection(String normalized) {
		for (Pattern p : reject) {
			if (p.matcher(normalized).find()) return true;
		}
		return false;
	}

	/**
	 * @return 1-7 for an unambiguous result, {@link #NONE} if the line is not a result line,
	 *         {@link #AMBIGUOUS} if it names several different numbers.
	 */
	public int parseResult(String normalized) {
		int found = NONE;
		for (Pattern p : result) {
			Matcher m = p.matcher(normalized);
			while (m.find()) {
				int n = digit(m);
				if (n == NONE) continue;
				if (found != NONE && found != n) return AMBIGUOUS;
				found = n;
			}
		}
		if (DYE_ROLL.matcher(normalized).find() && !MARKET_WORDS.matcher(normalized).find()) {
			if (found != NONE && found != 7) return AMBIGUOUS;
			found = 7;
		}
		if (found != NONE) {
			// Safety net: any other standalone digit 1-7 in the line makes it ambiguous
			// (e.g. "rolled a 4 and then a 6"). Neutral is better than a wrong win.
			Matcher m = STANDALONE_DIGIT.matcher(normalized);
			while (m.find()) {
				if (m.group().charAt(0) - '0' != found) return AMBIGUOUS;
			}
		}
		return found;
	}

	private static int digit(Matcher m) {
		if (m.groupCount() < 1) return NONE;
		String g = m.group(1);
		if (g == null || g.length() != 1) return NONE;
		int n = g.charAt(0) - '0';
		return (n >= 1 && n <= 7) ? n : NONE;
	}
}
