package com.rngaddons;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Settings button for RNGaddons in Mod Menu (Mods button -> RNGaddons -> gear icon).
 * Only loaded when Mod Menu is installed; without it the menu still opens with /rngaddons.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> RNGaddonsClient.createConfigScreen(parent);
	}
}
