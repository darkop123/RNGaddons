package com.archfiendslots.client;

import com.archfiendslots.core.RollSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Plays existing vanilla UI sounds locally (nothing is shipped, nothing persists). Driven by the discrete
 * events of a {@link RollSession}, so it does not depend on the frame rate. Never throws: a failing sound
 * must not break the animation.
 */
final class SoundController {
	void handle(int event, double masterVolume) {
		if (masterVolume <= 0.0) return;
		float v = (float) masterVolume;
		try {
			switch (event) {
				case RollSession.EV_TICK -> play(SoundEvents.UI_BUTTON_CLICK, 1.7f, 0.12f * v);
				case RollSession.EV_STOP_1 -> play(SoundEvents.UI_BUTTON_CLICK, 0.85f, 0.35f * v);
				case RollSession.EV_STOP_2 -> play(SoundEvents.UI_BUTTON_CLICK, 0.97f, 0.35f * v);
				case RollSession.EV_STOP_3 -> play(SoundEvents.UI_BUTTON_CLICK, 1.09f, 0.40f * v);
				case RollSession.EV_WIN_GOLD -> {
					play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 0.35f * v);
					play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f, 0.30f * v);
				}
				case RollSession.EV_WIN_DYE -> {
					play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.5f * v);
					play(SoundEvents.PLAYER_LEVELUP, 0.7f, 0.25f * v);
				}
				default -> { }
			}
		} catch (Throwable ignored) {
			// cosmetic only
		}
	}

	// forUI takes a plain SoundEvent in 26.1; some SoundEvents constants are Holder<SoundEvent>, so unwrap those.
	private static void play(Holder<SoundEvent> sound, float pitch, float volume) {
		play(sound.value(), pitch, volume);
	}

	private static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}
}
