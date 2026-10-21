package com.rngaddons.config;

import com.rngaddons.features.archfiend.core.Settings;

/**
 * Everything RNGaddons stores in config/rngaddons.json. One section per feature, so new features
 * simply add a field here (missing sections in an older file keep their defaults).
 */
public final class ModConfig {
	public int configVersion = 1;

	/** Feature "Archfiend Dice" (slot machine overlay). */
	public Settings archfiendDice = new Settings();

	public ModConfig sanitize() {
		if (archfiendDice == null) archfiendDice = new Settings();
		archfiendDice.sanitize();
		return this;
	}
}
