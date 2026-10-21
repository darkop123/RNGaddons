package com.archfiendslots.render;

/**
 * Minimal 2D drawing surface. This is the ONLY thing the renderers know about; the Minecraft
 * implementation (client/Gfx) is a thin adapter. Because of this the whole overlay can be rendered
 * headlessly (see the test sources) and the visuals can change without touching result detection.
 *
 * Coordinates are in GUI-scaled pixels at the root; after translate/scale they are in the local space.
 * Colours are ARGB.
 */
public interface Canvas {
	/** Screen size in GUI-scaled pixels. */
	int width();
	int height();

	void push();
	void pop();
	void translate(float x, float y);
	void scale(float s);

	void rect(int x, int y, int w, int h, int argb);

	/** Draws text with its top-left corner at (x, y) in the current local space (font height ~8). */
	void text(String s, int x, int y, int argb, boolean shadow);
	int textWidth(String s);
}
