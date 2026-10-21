package com.rngaddons.features.archfiend.render;

/**
 * Hand-drawn pixel art (no texture files needed, so no texture-API dependency and nothing to load or free).
 * The Archfiend Dye is an original stylised drawing, not a copy of the in-game item texture.
 */
public final class Sprites {
	private Sprites() {}

	// palette: '.' transparent
	private static int pal(char c) {
		return switch (c) {
			case '#' -> 0xFF4A3300; // dark gold outline
			case 'Y' -> 0xFFFFC72C; // gold
			case 'y' -> 0xFFE09A12; // gold shade
			case 'h' -> 0xFFFFF3A8; // gold highlight
			case 'c' -> 0xFF2A8FA8; // cyan outline
			case 'C' -> 0xFF59D6F2; // cyan
			case 'w' -> 0xFFE8FDFF; // white
			case 'r' -> 0xFF6E0A1A; // ruby outline
			case 'R' -> 0xFFE02040; // ruby
			case 'q' -> 0xFFFF8A9B; // ruby light
			case 'k' -> 0xFF14060C; // near-black
			case 'f' -> 0xFFB3201A; // flame dark
			case 'o' -> 0xFFFF7A1A; // flame orange
			case 'e' -> 0xFFFFD84A; // flame yellow
			case 'p' -> 0xFF6A2BA8; // purple
			case 'P' -> 0xFF9B59E0; // purple light
			case 'x' -> 0xFFFF2A4A; // glowing red eyes
			case 'L' -> 0xFF3B1458; // vial glass
			case 'm' -> 0xFF8A1C4E; // vial mid
			case 'M' -> 0xFFD62850; // vial bright
			case 'g' -> 0xFFFFE08A; // glowing core
			case 'G' -> 0xFFFFF8D8; // core centre
			case 'v' -> 0xFFB48CFF; // glass highlight
			case 'b' -> 0xFF6B3A1E; // cork
			case 'B' -> 0xFF9A5A2C; // cork light
			default -> 0;
		};
	}

	private static final String[] COIN = {
		"..####..",
		".#YYYY#.",
		"#YhhhyY#",
		"#YhyyyY#",
		"#YhyyyY#",
		"#YyyyyY#",
		".#YyyY#.",
		"..####..",
	};
	private static final String[] BAR = {
		"..####..",
		".#hhhh#.",
		"#hYYYYy#",
		"#hYhhYy#",   // coin mark
		"#hYhhYy#",
		"#YYYYyy#",
		"#yyyyyy#",
		".######.",
	};
	private static final String[] DIAMOND = {
		"..cccc..",
		".cwCCCc.",
		"cwCCCCCc",
		"cCCCCCCc",
		".cCCCCc.",
		"..cCCc..",
		"...cc...",
		"........",
	};
	private static final String[] RUBY = {
		"..rrrr..",
		".rqRRRr.",
		"rqRRRRRr",
		"rRRRRRRr",
		".rRRRRr.",
		"..rRRr..",
		"...rr...",
		"........",
	};
	private static final String[] FLAME = {
		"...ff...",
		"..fof...",
		"..fooff.",
		".fooeof.",
		".foeeof.",
		"fooeeeof",
		"fooeeeof",
		".ffoooff",
	};
	private static final String[] FIEND = {
		"p.p..p.p",
		"pp.pp.pp",
		".pppppp.",
		"pPxppxPp",
		"pppppppp",
		".pkkkkp.",
		"..pkkp..",
		"...pp...",
	};
	private static final String[][] SYMBOLS = {COIN, BAR, DIAMOND, RUBY, FLAME, FIEND};

	/** 3x5 digits 0-9. */
	private static final String[] DIGITS = {
		"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
		"111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111",
	};

	private static final String[] DYE = {
		"......kkkk......",
		".....kbBBbk.....",
		".....kbbbbk.....",
		"......kkkk......",
		".....kLvLLk.....",
		".....kLvLLk.....",
		"....kLLvLLLk....",
		"..kkLLmmmmLLkk..",
		".kLLmmMMMMmmLLk.",
		".kLmMMMggMMMmLk.",
		"kLLmMMgGGgMMmLLk",
		"kLLmMMgGGgMMmLLk",
		"kLLmMMMggMMMmLLk",
		".kLmmMMMMMMmmLk.",
		"..kLLmmmmmmLLk..",
		"...kkkkkkkkkk...",
	};

	public static final int DYE_SIZE = 16;

	/**
	 * Draws a sprite with pixel size {@code px}, clipped vertically to [clipTop, clipBottom] (local coords).
	 * x/y are the top-left in local coordinates (integers).
	 */
	public static void drawSymbol(Canvas c, int symbol, int x, int y, int px, int clipTop, int clipBottom, int alphaMul255) {
		if (symbol >= Symbols.NUM_BASE + 1) {
			drawDigit(c, symbol - Symbols.NUM_BASE, x, y, px, clipTop, clipBottom, alphaMul255);
			return;
		}
		drawBitmap(c, SYMBOLS[symbol], 8, x, y, px, clipTop, clipBottom, 1f, alphaMul255);
	}

	public static void drawDye(Canvas c, int x, int y, int px, float widthFactor, int alphaMul255) {
		drawBitmap(c, DYE, DYE_SIZE, x, y, px, Integer.MIN_VALUE, Integer.MAX_VALUE, widthFactor, alphaMul255);
	}

	/** Big digit (3x5) for the number symbols, in gold-white with a dark outline. */
	private static void drawDigit(Canvas c, int digit, int x, int y, int px, int clipTop, int clipBottom, int a) {
		String bits = DIGITS[digit];
		// 3x5 digit (pixel size 1.5x) centred inside the 8x8 cell
		int s = Math.max(1, px * 3 / 2);
		int ox = x + (8 * px - 3 * s) / 2;
		int oy = y + (8 * px - 5 * s) / 2;
		for (int pass = 0; pass < 2; pass++) {
			for (int r = 0; r < 5; r++) {
				for (int col = 0; col < 3; col++) {
					if (bits.charAt(r * 3 + col) != '1') continue;
					int rx = ox + col * s;
					int ry = oy + r * s;
					if (pass == 0) {
						clippedRect(c, rx - 1, ry - 1, s + 2, s + 2, clipTop, clipBottom, withAlpha(0xFF14060C, a));
					} else {
						clippedRect(c, rx, ry, s, s, clipTop, clipBottom, withAlpha(0xFFFFE9A3, a));
					}
				}
			}
		}
	}

	private static void drawBitmap(Canvas c, String[] rows, int size, int x, int y, int px, int clipTop, int clipBottom,
	                               float widthFactor, int a) {
		float wf = Math.max(0.04f, Math.abs(widthFactor));
		float cx = size * px / 2f;
		for (int r = 0; r < size; r++) {
			String row = rows[r];
			for (int col = 0; col < size; col++) {
				int color = pal(row.charAt(col));
				if (color == 0) continue;
				// horizontal squash around the centre (fake Y-axis rotation)
				float x0 = cx + (col * px - cx) * wf;
				float x1 = cx + ((col + 1) * px - cx) * wf;
				int rx = x + Math.round(Math.min(x0, x1));
				int rw = Math.max(1, Math.round(Math.abs(x1 - x0)));
				clippedRect(c, rx, y + r * px, rw, px, clipTop, clipBottom, withAlpha(color, a));
			}
		}
	}

	static void clippedRect(Canvas c, int x, int y, int w, int h, int clipTop, int clipBottom, int argb) {
		int y0 = Math.max(y, clipTop);
		int y1 = Math.min(y + h, clipBottom);
		if (y1 <= y0 || w <= 0) return;
		c.rect(x, y0, w, y1 - y0, argb);
	}

	/** Multiplies the alpha channel by a/255. */
	public static int withAlpha(int argb, int a255) {
		int a = (argb >>> 24) * Math.max(0, Math.min(255, a255)) / 255;
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
