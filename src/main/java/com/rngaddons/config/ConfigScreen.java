package com.rngaddons.config;

import com.rngaddons.RNGaddonsClient;
import com.rngaddons.features.archfiend.core.DiceType;
import com.rngaddons.features.archfiend.core.Settings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * RNGaddons settings menu in the style of SkyHanni's config: dark window, title bar, sidebar with one
 * section per feature (and its pages), option cards (title + grey description + control) on the right.
 *
 * Opened from Mod Menu (Mods button) or with /rngaddons. Every change is applied and saved immediately.
 * Only vanilla widgets (Button) are used, so no extra config library is needed.
 *
 * Adding a feature later: add its pages to PAGES (section name = feature name) and a case in init().
 */
public final class ConfigScreen extends Screen {
	/** {section, page}. Pages of the same section are listed under one heading in the sidebar. */
	private static final String[][] PAGES = {
		{"Archfiend Dice", "General"},
		{"Archfiend Dice", "Sound & Effects"},
		{"Archfiend Dice", "Preview"},
		{"Archfiend Dice", "Advanced"},
	};
	private static int page; // remembered while the game runs

	private static final int SIDEBAR = 112;
	private static final int HEADER = 24;
	private static final int ROW = 30;
	private static final int GAP = 4;

	// colours (ARGB), close to SkyHanni's dark theme
	private static final int C_WINDOW = 0xF0121212;
	private static final int C_BORDER = 0xFF3C3C3C;
	private static final int C_HEADER = 0xFF1B1B1B;
	private static final int C_SIDEBAR = 0xFF171717;
	private static final int C_CARD = 0xFF222222;
	private static final int C_CARD_EDGE = 0xFF2E2E2E;
	private static final int C_ACCENT = 0xFFFFAA00;
	private static final int C_TITLE = 0xFFFFFFFF;
	private static final int C_DESC = 0xFF9A9A9A;
	private static final int C_VALUE = 0xFFFFE08A;

	private final Screen parent;
	private final RNGaddonsClient mod;
	private final List<Row> rows = new ArrayList<>();

	private int px, py, pw, ph;   // window
	private int cx, cw;           // content column

	/** One option card: text drawn by the backdrop, controls are real buttons. */
	private record Row(int y, String title, String desc, String value, int controlWidth) {}

	public ConfigScreen(Screen parent, RNGaddonsClient mod) {
		super(Component.literal(RNGaddonsClient.NAME));
		this.parent = parent;
		this.mod = mod;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		pw = Math.min(width - 16, 460);
		ph = Math.min(height - 16, 260);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		cx = px + SIDEBAR + 8;
		cw = pw - SIDEBAR - 16;
		rows.clear();

		addRenderableOnly(new Backdrop());

		for (int i = 0; i < PAGES.length; i++) {
			final int idx = i;
			Component label = Component.literal(PAGES[i][1]);
			if (i == page) label = label.copy().withStyle(ChatFormatting.GOLD);
			addRenderableWidget(Button.builder(label, b -> {
				page = idx;
				rebuildWidgets();
			}).bounds(px + 8, pageY(i), SIDEBAR - 14, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(px + 8, py + ph - 26, SIDEBAR - 14, 20).build());

		Settings s = mod.archfiend().settings();
		switch (page) {
			case 0 -> {
				toggle(0, "Enable slot machine", "Replace the dice roll with the slot machine.",
					s.enabled, v -> s.enabled = v);
				toggle(1, "Archfiend Dice", "Use it for the normal dice.",
					s.archfiendDiceEnabled, v -> s.archfiendDiceEnabled = v);
				toggle(2, "High Class Archfiend Dice", "Use it for the High Class dice.",
					s.highClassArchfiendDiceEnabled, v -> s.highClassArchfiendDiceEnabled = v);
				stepper(3, "Animation length", "Spin time of a normal roll.",
					String.format(Locale.ROOT, "%.2f s", s.durationSeconds),
					() -> s.durationSeconds = round(s.durationSeconds - 0.25, 100),
					() -> s.durationSeconds = round(s.durationSeconds + 0.25, 100));
				stepper(4, "Overlay size", "Size of the slot machine.",
					String.format(Locale.ROOT, "%.1fx", s.overlayScale),
					() -> s.overlayScale = round(s.overlayScale - 0.1, 10),
					() -> s.overlayScale = round(s.overlayScale + 0.1, 10));
			}
			case 1 -> {
				toggle(0, "Sounds", "Reel ticks and jackpot sounds.",
					s.soundEnabled, v -> s.soundEnabled = v);
				stepper(1, "Volume", "Loudness of the slot sounds.",
					Math.round(s.soundVolume * 100) + "%",
					() -> s.soundVolume = round(s.soundVolume - 0.1, 10),
					() -> s.soundVolume = round(s.soundVolume + 0.1, 10));
				toggle(2, "Particles", "Coins and sparks on a jackpot.",
					s.particlesEnabled, v -> s.particlesEnabled = v);
				toggle(3, "Screen shake", "Small shake when a jackpot lands.",
					s.screenShakeEnabled, v -> s.screenShakeEnabled = v);
			}
			case 2 -> {
				action(0, "Normal roll", "Local preview, not a real roll.", "Play",
					() -> preview(DiceType.ARCHFIEND_DICE, 4));
				action(1, "Gold jackpot (6)", "Preview: +15,000,000 coins.", "Play",
					() -> preview(DiceType.ARCHFIEND_DICE, 6));
				action(2, "High Class jackpot (6)", "Preview: +100,000,000 coins.", "Play",
					() -> preview(DiceType.HIGH_CLASS_ARCHFIEND_DICE, 6));
				action(3, "Archfiend Dye (7)", "Preview of the portal and the dye.", "Play",
					() -> preview(DiceType.ARCHFIEND_DICE, 7));
			}
			default -> {
				stepper(0, "Result timeout", "Max wait for Hypixel's answer.",
					(s.resultTimeoutMs / 1000) + " s",
					() -> s.resultTimeoutMs -= 1000,
					() -> s.resultTimeoutMs += 1000);
				toggle(1, "Message capture (debug)", "Log chat lines during a roll.",
					s.debugLogMessages, v -> s.debugLogMessages = v);
				toggle(2, "Name fallback", "Detect by item name. Can be spoofed.",
					s.allowNameFallback, v -> s.allowNameFallback = v);
				action(3, "Reset Archfiend Dice", "Back to the default values.", "Reset",
					() -> { mod.resetArchfiend(); rebuildWidgets(); });
			}
		}
	}

	/** Sidebar position of page i; a new section adds room for its heading. */
	private int pageY(int i) {
		int y = py + HEADER + 6;
		String section = null;
		for (int k = 0; k <= i; k++) {
			if (!PAGES[k][0].equals(section)) {
				section = PAGES[k][0];
				y += 12;
			}
			if (k < i) y += 24;
		}
		return y;
	}

	private int rowY(int i) {
		return py + HEADER + 22 + i * ROW;
	}

	// ------------------------------------------------------------------ option builders

	private void toggle(int i, String title, String desc, boolean value, Consumer<Boolean> set) {
		int y = rowY(i);
		int bw = 46;
		rows.add(new Row(y, title, desc, null, bw));
		Component label = value
			? Component.literal("ON").withStyle(ChatFormatting.GREEN)
			: Component.literal("OFF").withStyle(ChatFormatting.RED);
		addRenderableWidget(Button.builder(label, b -> {
			set.accept(!value);
			changed();
		}).bounds(cx + cw - bw - 5, y + (ROW - GAP - 20) / 2, bw, 20).build());
	}

	private void stepper(int i, String title, String desc, String value, Runnable dec, Runnable inc) {
		int y = rowY(i);
		int total = 20 + 48 + 20;
		rows.add(new Row(y, title, desc, value, total));
		int bx = cx + cw - total - 5;
		int by = y + (ROW - GAP - 20) / 2;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> { dec.run(); changed(); })
			.bounds(bx, by, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> { inc.run(); changed(); })
			.bounds(bx + 68, by, 20, 20).build());
	}

	private void action(int i, String title, String desc, String button, Runnable run) {
		int y = rowY(i);
		int bw = 56;
		rows.add(new Row(y, title, desc, null, bw));
		addRenderableWidget(Button.builder(Component.literal(button), b -> run.run())
			.bounds(cx + cw - bw - 5, y + (ROW - GAP - 20) / 2, bw, 20).build());
	}

	private void changed() {
		mod.applyAndSave(); // clamps, applies live and writes rngaddons.json
		rebuildWidgets();
	}

	private void preview(DiceType type, int result) {
		mod.applyAndSave();
		this.minecraft.setScreen(null); // close so the overlay is visible
		mod.archfiend().preview(type, result);
	}

	private static double round(double v, int factor) {
		return Math.round(v * factor) / (double) factor;
	}

	@Override
	public void onClose() {
		mod.applyAndSave();
		this.minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------------ drawing

	private String fit(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		String dots = "...";
		int end = s.length();
		while (end > 0 && font.width(s.substring(0, end) + dots) > maxWidth) end--;
		return s.substring(0, end) + dots;
	}

	/** Draws the window, sidebar and option cards behind the buttons (registered first, receives no input). */
	private final class Backdrop extends AbstractWidget {
		Backdrop() {
			super(0, 0, 0, 0, Component.empty());
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			// window + border
			g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER);
			g.fill(px, py, px + pw, py + ph, C_WINDOW);
			// title bar
			g.fill(px, py, px + pw, py + HEADER, C_HEADER);
			g.fill(px, py + HEADER, px + pw, py + HEADER + 1, C_BORDER);
			String version = FabricLoader.getInstance().getModContainer(RNGaddonsClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
			g.text(font, RNGaddonsClient.NAME, px + 8, py + 8, C_ACCENT, true);
			g.text(font, "v" + version, px + 12 + font.width(RNGaddonsClient.NAME), py + 8, C_DESC, false);
			String hint = "/" + RNGaddonsClient.MOD_ID;
			g.text(font, hint, px + pw - 8 - font.width(hint), py + 8, C_DESC, false);
			// sidebar with section headings
			g.fill(px, py + HEADER + 1, px + SIDEBAR, py + ph, C_SIDEBAR);
			g.fill(px + SIDEBAR, py + HEADER + 1, px + SIDEBAR + 1, py + ph, C_BORDER);
			String section = null;
			for (int i = 0; i < PAGES.length; i++) {
				if (!PAGES[i][0].equals(section)) {
					section = PAGES[i][0];
					g.text(font, fit(section.toUpperCase(Locale.ROOT), SIDEBAR - 14), px + 8, pageY(i) - 11, C_DESC, false);
				}
			}
			int sel = pageY(page);
			g.fill(px + 3, sel + 2, px + 5, sel + 18, C_ACCENT);
			// page heading: "Archfiend Dice > General"
			String heading = PAGES[page][0] + " > " + PAGES[page][1];
			g.text(font, fit(heading, cw), cx, py + HEADER + 8, C_ACCENT, false);
			// option cards
			for (Row r : rows) {
				int y = r.y();
				g.fill(cx, y, cx + cw, y + ROW - GAP, C_CARD_EDGE);
				g.fill(cx + 1, y + 1, cx + cw - 1, y + ROW - GAP - 1, C_CARD);
				int textMax = cw - r.controlWidth() - 20;
				g.text(font, fit(r.title(), textMax), cx + 6, y + 4, C_TITLE, false);
				g.text(font, fit(r.desc(), textMax), cx + 6, y + 15, C_DESC, false);
				if (r.value() != null) {
					int mid = cx + cw - 5 - r.controlWidth() + 20 + 24;
					g.text(font, r.value(), mid - font.width(r.value()) / 2, y + (ROW - GAP - 8) / 2, C_VALUE, true);
				}
			}
			// footer
			String foot = "Saved to config/rngaddons.json";
			g.text(font, fit(foot, cw), cx, py + ph - 12, C_DESC, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			// decorative only
		}
	}
}
