package com.archfiendslots.client;

import com.archfiendslots.core.DiceType;
import com.archfiendslots.core.RollController;
import com.archfiendslots.core.RollSession;
import com.archfiendslots.core.Settings;
import com.archfiendslots.render.SlotMachineRenderer;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Client entrypoint. Wiring only; all decisions live in the pure classes under core/ and render/.
 *
 * Events used (no mixins, no packet access, no server communication):
 *   UseItemCallback                       - the player right-clicked with an item
 *   ClientReceiveMessageEvents.GAME       - system/actionbar messages (Hypixel's roll result lines)
 *   ClientTickEvents.END_CLIENT_TICK      - timeouts, sounds
 *   ClientPlayConnectionEvents.DISCONNECT - abort everything
 *   HudElementRegistry                    - the overlay
 *   ClientCommandRegistrationCallback     - /archfiendslots (demo, capture, reload)
 */
public final class ArchfiendSlotsClient implements ClientModInitializer {
	public static final String MOD_ID = "archfiendslots";
	static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private RollController controller;
	private final SlotMachineRenderer renderer = new SlotMachineRenderer();
	private final Gfx gfx = new Gfx();
	private final SoundController sound = new SoundController();
	private boolean renderFailed;

	private static long now() {
		return System.nanoTime() / 1_000_000L; // monotonic
	}

	@Override
	public void onInitializeClient() {
		controller = new RollController(ConfigManager.load(), LOG::info);

		UseItemCallback.EVENT.register(this::onUseItem);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			try {
				controller.onGameMessage(message.getString(), now());
			} catch (Throwable t) {
				LOG.warn("Ignoring message after error: {}", t.toString());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> onTick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> abortAll());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> abortAll());

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "slot_machine"), this::extract);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal("archfiendslots")
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
						ctx.getSource().sendFeedback(Component.literal("Archfiend Slots: " + controller.describe(now())
							+ ", renderFailed=" + renderFailed));
						return 1;
					}))
				.then(ClientCommands.literal("reload")
					.executes(ctx -> reload(ctx.getSource()::sendFeedback)))));

		LOG.info("Archfiend Slots loaded (client-side only; cosmetic overlay, never changes game mechanics).");
	}

	// ------------------------------------------------------------------ events

	private InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		try {
			Minecraft mc = Minecraft.getInstance();
			// The event is global; in singleplayer it also fires for the server-side player. Only react to ours.
			if (player != mc.player) return InteractionResult.PASS;
			DiceType type = DiceDetector.detect(player.getItemInHand(hand), controller.settings().allowNameFallback);
			if (type != null) {
				renderFailed = false; // a rendering error must never disable the overlay for good
				controller.onItemUse(type, now());
			}
		} catch (Throwable t) {
			LOG.warn("Ignoring item use after error: {}", t.toString());
		}
		return InteractionResult.PASS; // never consume or cancel the click: the server must see it
	}

	private void onTick() {
		try {
			long t = now();
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) {
				abortAll();
				return;
			}
			RollSession s = controller.current(t);
			if (s == null) {
				renderer.reset();
				return;
			}
			Settings cfg = controller.settings();
			int ev;
			while ((ev = s.pollEvent(t)) != RollSession.EV_NONE) {
				if (cfg.soundEnabled) sound.handle(ev, cfg.soundVolume);
			}
		} catch (Throwable t) {
			LOG.warn("Tick error: {}", t.toString());
		}
	}

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (renderFailed) return;
		long t = now();
		RollSession s = controller.current(t);
		if (s == null) return;
		gfx.begin(graphics);
		try {
			renderer.render(gfx, s, t, controller.settings());
		} catch (Throwable e) {
			renderFailed = true; // stop drawing this roll; re-enabled by the next dice use
			LOG.error("Overlay rendering failed; this roll is cancelled.", e);
			controller.cancelAll();
		} finally {
			gfx.end();
		}
	}

	private void abortAll() {
		controller.cancelAll();
		renderer.reset();
	}

	// ------------------------------------------------------------------ /archfiendslots

	private int demo(Consumer<Component> out, String dice, int result) {
		DiceType type = switch (dice.toLowerCase(java.util.Locale.ROOT)) {
			case "normal", "archfiend", "std" -> DiceType.ARCHFIEND_DICE;
			case "high", "highclass", "hc" -> DiceType.HIGH_CLASS_ARCHFIEND_DICE;
			default -> null;
		};
		if (type == null) {
			out.accept(Component.literal("Dice must be 'normal' or 'high'."));
			return 0;
		}
		renderFailed = false;
		if (!controller.startDemo(type, result, now())) {
			out.accept(Component.literal("An animation is already running."));
			return 0;
		}
		out.accept(Component.literal("Demo animation (local preview only, not a real roll): " + type.title + ", result " + result));
		return 1;
	}

	private int capture(Consumer<Component> out, boolean on) {
		Settings s = controller.settings();
		s.debugLogMessages = on;
		ConfigManager.save(s);
		out.accept(Component.literal("Message capture " + (on ? "ON" : "OFF")
			+ ": while a roll is pending, incoming lines are written to latest.log as '[pending roll #n] ...'."));
		return 1;
	}

	private int reload(Consumer<Component> out) {
		controller.updateSettings(ConfigManager.load());
		renderFailed = false;
		out.accept(Component.literal("Archfiend Slots config reloaded from " + ConfigManager.path().getFileName()));
		return 1;
	}
}
