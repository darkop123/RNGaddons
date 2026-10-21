package com.rngaddons.features.archfiend.core;

import java.util.ArrayList;
import java.util.List;

/**
 * User settings (serialised to config/rngaddons.json, section "archfiendDice").
 * Public fields with defaults so a missing key in the JSON simply keeps the default.
 */
public final class Settings {
	public boolean enabled = true;
	/** Base duration of a normal roll in seconds (clamped to 2.0 - 3.0, so even a 7 stays <= 3.5 s). */
	public double durationSeconds = 3.0;
	public boolean soundEnabled = true;
	/** 0.0 - 1.0 */
	public double soundVolume = 0.6;
	public boolean particlesEnabled = true;
	public boolean screenShakeEnabled = true;
	/** 0.5 - 2.0, multiplies the automatic size. */
	public double overlayScale = 1.0;
	public boolean archfiendDiceEnabled = true;
	public boolean highClassArchfiendDiceEnabled = true;
	/** How long to wait for the server result before ending neutrally (2000 - 15000). */
	public int resultTimeoutMs = 5000;
	/** Fallback detection by exact display name if the item has no SkyBlock id. Off by default (spoofable by renaming). */
	public boolean allowNameFallback = false;
	/** Log every game message that arrives while a roll is pending (to learn the real Hypixel wording). */
	public boolean debugLogMessages = false;
	/** Extra regexes; group 1 must capture the rolled number 1-7. */
	public List<String> extraResultPatterns = new ArrayList<>();
	/** Extra regexes marking a rejected roll (no coins, cooldown, ...). */
	public List<String> extraRejectPatterns = new ArrayList<>();

	/** Repairs out-of-range or null values after loading. */
	public Settings sanitize() {
		if (!(durationSeconds >= 2.0)) durationSeconds = durationSeconds != durationSeconds ? 3.0 : 2.0;
		if (durationSeconds > 3.0) durationSeconds = 3.0;
		if (!(soundVolume >= 0.0)) soundVolume = soundVolume != soundVolume ? 0.6 : 0.0;
		if (soundVolume > 1.0) soundVolume = 1.0;
		if (!(overlayScale >= 0.5)) overlayScale = overlayScale != overlayScale ? 1.0 : 0.5;
		if (overlayScale > 2.0) overlayScale = 2.0;
		if (resultTimeoutMs < 2000) resultTimeoutMs = 2000;
		if (resultTimeoutMs > 15000) resultTimeoutMs = 15000;
		if (extraResultPatterns == null) extraResultPatterns = new ArrayList<>();
		if (extraRejectPatterns == null) extraRejectPatterns = new ArrayList<>();
		return this;
	}

	public boolean isEnabledFor(DiceType type) {
		if (!enabled || type == null) return false;
		return type.highClass ? highClassArchfiendDiceEnabled : archfiendDiceEnabled;
	}
}
