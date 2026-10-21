package com.archfiendslots.client;

import com.archfiendslots.core.DiceType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Identifies the two supported dice by their SkyBlock item id (custom_data component, key "id").
 * Hypixel stores the id there (e.g. {id:"ARCHFIEND_DICE"}); the layout is the same one other SkyBlock mods read.
 *
 * If an item carries an id that is not one of the two dice, it is NOT a dice - the name fallback is never
 * consulted, so other dice (e.g. Sinful Dice) and normal items are never affected.
 * The optional name fallback (settings.allowNameFallback, default off) only applies to items with no id at all
 * and can be spoofed by renaming an item.
 */
final class DiceDetector {
	private DiceDetector() {}

	static DiceType detect(ItemStack stack, boolean allowNameFallback) {
		if (stack == null || stack.isEmpty()) return null;
		String id = readSkyblockId(stack);
		if (!id.isEmpty()) return DiceType.fromSkyBlockId(id);
		if (allowNameFallback) return DiceType.fromDisplayName(stack.getHoverName().getString());
		return null;
	}

	private static String readSkyblockId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return "";
		CompoundTag tag = data.copyTag();
		return tag.getString("id").orElse("");
	}
}
