package com.rngaddons;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rngaddons.config.ConfigManager;
import com.rngaddons.config.ConfigScreen;
import com.rngaddons.config.ModConfig;
import com.rngaddons.features.archfiend.ArchfiendDiceFeature;
import com.rngaddons.features.archfiend.core.DiceType;
import com.rngaddons.features.archfiend.core.Settings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * RNGaddons client entrypoint: loads the config, starts the features, registers /rngaddons and the menu.
 * Each feature lives in its own package under com.rngaddons.features (currently: archfiend).
 * Purely client-side and cosmetic: never sends anything to the server, never changes game mechanics.
 */
public final class RNGaddonsClient implements ClientModInitializer {
	public static final String MOD_ID = "rngaddons";
	public static final String NAME = "RNGaddons";
	public static final String AUTHOR = "Chris_t0pf";
	static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private static RNGaddonsClient instance;

	private ModConfig config;
	private ArchfiendDiceFeature archfiend;
	private boolean openMenuNextTick;

	@Override
	public void onInitializeClient() {
		instance = this;
		config = ConfigManager.load();

		archfiend = new ArchfiendDiceFeature(config.archfiendDice);
		archfiend.register(MOD_ID);

		// The chat screen closes right after a command, so the menu is opened on the next tick.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (openMenuNextTick) {
				openMenuNextTick = false;
				mc.setScreen(new ConfigScreen(null, this));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal(MOD_ID)
				.executes(ctx -> { openMenuNextTick = true; return 1; })
				.then(ClientCommands.literal("config")
					.executes(ctx -> { openMenuNextTick = true; return 1; }))
				.then(ClientCommands.literal("archfiend")
					.then(ClientCommands.literal("demo")
						.then(ClientCommands.argument("dice", StringArgumentType.word())
							.then(ClientCommands.argument("result", IntegerArgumentType.integer(1, 7))
								.executes(ctx -> demo(ctx.getSource()::sendFeedback,
									StringArgumentType.getString(ctx, "dice"),
									IntegerArgumentType.getInteger(ctx, "result"))))))
					.then(ClientCommands.literal("capture")
						.then(ClientCommands.argument("on", BoolArgumentType.bool())
							.executes(ctx -> capture(ctx.getSource()::sendFeedback, BoolArgumentType.getBool(ctx, "on")))))
					.then(ClientCommands.literal("status")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(NAME + " / Archfiend Dice: " + archfiend.status()));
							return 1;
						})))
				.then(ClientCommands.literal("reload")
					.executes(ctx -> reload(ctx.getSource()::sendFeedback)))));

		LOG.info("{} loaded (client-side only; cosmetic, never changes game mechanics).", NAME);
	}

	// ------------------------------------------------------------------ used by the menu / Mod Menu

	public static Screen createConfigScreen(Screen parent) {
		return new ConfigScreen(parent, instance);
	}

	public ArchfiendDiceFeature archfiend() {
		return archfiend;
	}

	/** Clamps and applies the live settings of all features, then writes rngaddons.json. */
	public void applyAndSave() {
		config.sanitize();
		archfiend.updateSettings(config.archfiendDice);
		ConfigManager.save(config);
	}

	public void resetArchfiend() {
		config.archfiendDice = new Settings();
		applyAndSave();
	}

	// ------------------------------------------------------------------ commands

	private int demo(Consumer<Component> out, String dice, int result) {
		DiceType type = switch (dice.toLowerCase(Locale.ROOT)) {
			case "normal", "archfiend", "std" -> DiceType.ARCHFIEND_DICE;
			case "high", "highclass", "hc" -> DiceType.HIGH_CLASS_ARCHFIEND_DICE;
			default -> null;
		};
		if (type == null) {
			out.accept(Component.literal("Dice must be 'normal' or 'high'."));
			return 0;
		}
		if (!archfiend.preview(type, result)) {
			out.accept(Component.literal("An animation is already running."));
			return 0;
		}
		out.accept(Component.literal("Demo animation (local preview only, not a real roll): " + type.title + ", result " + result));
		return 1;
	}

	private int capture(Consumer<Component> out, boolean on) {
		config.archfiendDice.debugLogMessages = on;
		applyAndSave();
		out.accept(Component.literal("Message capture " + (on ? "ON" : "OFF")
			+ ": while a roll is pending, incoming lines are written to latest.log as '[pending roll #n] ...'."));
		return 1;
	}

	private int reload(Consumer<Component> out) {
		config = ConfigManager.load();
		applyAndSave();
		out.accept(Component.literal(NAME + " config reloaded from " + ConfigManager.path().getFileName()));
		return 1;
	}
}
