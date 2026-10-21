package com.rngaddons.features.archfiend;

import com.rngaddons.features.archfiend.core.DiceType;
import com.rngaddons.features.archfiend.core.RollController;
import com.rngaddons.features.archfiend.core.RollSession;
import com.rngaddons.features.archfiend.core.Settings;
import com.rngaddons.features.archfiend.render.SlotMachineRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RNGaddons feature "Archfiend Dice": replaces the dice roll visuals with a slot machine overlay.
 * Wiring only; all decisions live in the pure classes under core/ and render/.
 *
 * Events used (no mixins, no packet access, no server communication):
 *   UseItemCallback + right-click key   - the player used a dice (either signal starts the animation)
 *   ClientReceiveMessageEvents.GAME     - Hypixel's roll result / rejection lines
 *   ClientTickEvents.END_CLIENT_TICK    - timeouts, sounds
 *   ClientPlayConnectionEvents          - abort everything on join/disconnect
 *   HudElementRegistry                  - the overlay
 */
public final class ArchfiendDiceFeature {
	static final Logger LOG = LoggerFactory.getLogger("rngaddons/archfiend");

	private final RollController controller;
	private final SlotMachineRenderer renderer = new SlotMachineRenderer();
	private final Gfx gfx = new Gfx();
	private final SoundController sound = new SoundController();
	private boolean renderFailed;
	private boolean useWasDown;

	public ArchfiendDiceFeature(Settings settings) {
		this.controller = new RollController(settings, LOG::info);
	}

	static long now() {
		return System.nanoTime() / 1_000_000L; // monotonic
	}

	public void register(String modId) {
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
			Identifier.fromNamespaceAndPath(modId, "archfiend_slot_machine"), this::extract);
	}

	// ------------------------------------------------------------------ public API (menu, commands)

	public Settings settings() {
		return controller.settings();
	}

	/** Clamps and applies (new or edited) settings live. Saving is done by the caller. */
	public void updateSettings(Settings s) {
		controller.updateSettings(s);
	}

	/** Local, clearly labelled preview. Returns false if an animation is already running. */
	public boolean preview(DiceType type, int result) {
		renderFailed = false;
		return controller.startDemo(type, result, now());
	}

	public String status() {
		return controller.describe(now()) + ", renderFailed=" + renderFailed;
	}

	// ------------------------------------------------------------------ events

	private InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		try {
			Minecraft mc = Minecraft.getInstance();
			// The event is global; in singleplayer it also fires for the server-side player. Only react to ours.
			if (player != mc.player) return InteractionResult.PASS;
			DiceType type = DiceDetector.detect(player.getItemInHand(hand), controller.settings().allowNameFallback);
			if (controller.settings().debugLogMessages) {
				LOG.info("Item use: {} -> {}", DiceDetector.describe(player.getItemInHand(hand)), type);
			}
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
			// Second, independent trigger: the right-click key itself. Other mods can swallow UseItemCallback,
			// so we also watch the key going down while a dice is held. onItemUse ignores a second start.
			boolean useDown = mc.options.keyUse.isDown();
			if (useDown && !useWasDown && mc.screen == null) {
				DiceType held = DiceDetector.detect(mc.player.getMainHandItem(), controller.settings().allowNameFallback);
				if (held == null) held = DiceDetector.detect(mc.player.getOffhandItem(), controller.settings().allowNameFallback);
				if (held != null) {
					renderFailed = false;
					controller.onItemUse(held, t);
				}
			}
			useWasDown = useDown;
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
}
