package com.rngaddons.features.archfiend;

import com.rngaddons.features.archfiend.render.Canvas;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** Headless Canvas backed by a BufferedImage; used only for previews/tests (java.awt, no Minecraft). */
public final class ImageCanvas implements Canvas {
	public final BufferedImage image;
	private final Graphics2D g;
	private final int w, h;
	private final ArrayDeque<AffineTransform> stack = new ArrayDeque<>();
	private final Font font = new Font(Font.MONOSPACED, Font.BOLD, 8);
	public int rectCalls;

	public ImageCanvas(int w, int h) {
		this.w = w;
		this.h = h;
		image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setFont(font);
	}

	/** Fake "game world" backdrop so overlay transparency is visible. */
	public void fillBackground() {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int shade = 60 + (y * 70 / h) + (((x / 8) + (y / 8)) % 2 == 0 ? 8 : 0);
				image.setRGB(x, y, 0xFF000000 | (shade / 2) << 16 | (shade + 20) << 8 | (shade / 2 + 30));
			}
		}
	}

	public void dispose() { g.dispose(); }

	@Override public int width() { return w; }
	@Override public int height() { return h; }
	@Override public void push() { stack.push(g.getTransform()); }
	@Override public void pop() { g.setTransform(stack.pop()); }
	@Override public void translate(float x, float y) { g.translate(x, y); }
	@Override public void scale(float s) { g.scale(s, s); }

	@Override
	public void rect(int x, int y, int rw, int rh, int argb) {
		rectCalls++;
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(new Color(argb, true));
		g.fillRect(x, y, rw, rh);
	}

	@Override
	public void text(String s, int x, int y, int argb, boolean shadow) {
		if (shadow) {
			g.setColor(new Color((argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2), true));
			g.drawString(s, x + 1, y + 8);
		}
		g.setColor(new Color(argb, true));
		g.drawString(s, x, y + 7);
	}

	@Override
	public int textWidth(String s) {
		return s.length() * 6;
	}
}
