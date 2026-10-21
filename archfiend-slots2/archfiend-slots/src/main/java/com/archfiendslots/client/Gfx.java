package com.archfiendslots.client;

import com.archfiendslots.render.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Adapter from the mod's {@link Canvas} to Minecraft 26.1's {@link GuiGraphicsExtractor}.
 *
 * This class and {@link ArchfiendSlotsClient}, {@link DiceDetector}, {@link SoundController} and
 * {@link ConfigManager} are the ONLY places that touch Minecraft/Fabric APIs. The Minecraft calls used here:
 *   graphics.fill(x1, y1, x2, y2, argb)
 *   graphics.text(font, String, x, y, argb, shadow)
 *   graphics.pose().pushMatrix() / popMatrix() / translate(x, y) / scale(x, y)   (JOML Matrix3x2fStack)
 * If a future version renames one of them, this is the only file to fix.
 */
final class Gfx implements Canvas {
	private GuiGraphicsExtractor g;
	private Font font;
	private int w;
	private int h;
	private int depth;

	void begin(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		this.g = graphics;
		this.font = mc.font;
		this.w = mc.getWindow().getGuiScaledWidth();
		this.h = mc.getWindow().getGuiScaledHeight();
		this.depth = 0;
	}

	/** Restores the pose stack even if rendering threw half-way. */
	void end() {
		while (depth > 0) pop();
		this.g = null;
	}

	@Override public int width() { return w; }
	@Override public int height() { return h; }

	@Override
	public void push() {
		g.pose().pushMatrix();
		depth++;
	}

	@Override
	public void pop() {
		if (depth <= 0) return;
		g.pose().popMatrix();
		depth--;
	}

	@Override public void translate(float x, float y) { g.pose().translate(x, y); }
	@Override public void scale(float s) { g.pose().scale(s, s); }

	@Override
	public void rect(int x, int y, int rw, int rh, int argb) {
		if (rw <= 0 || rh <= 0) return;
		g.fill(x, y, x + rw, y + rh, argb);
	}

	@Override
	public void text(String s, int x, int y, int argb, boolean shadow) {
		g.text(font, s, x, y, argb, shadow);
	}

	@Override
	public int textWidth(String s) {
		return font.width(s);
	}
}
