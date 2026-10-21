package com.archfiendslots;

import com.archfiendslots.core.DiceType;
import com.archfiendslots.core.RollSession;
import com.archfiendslots.core.Settings;
import com.archfiendslots.render.SlotMachineRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders key frames to PNG files with a headless canvas (developer tool, not part of the mod).
 * Usage: java PreviewMain <outDir>
 */
public final class PreviewMain {
	public static void main(String[] args) throws Exception {
		File out = new File(args.length > 0 ? args[0] : "preview");
		out.mkdirs();
		Settings cfg = new Settings().sanitize();
		int[] results = {4, 6, 7};
		for (DiceType dice : DiceType.values()) {
			for (int result : results) {
				if (dice.highClass && result == 4) continue;
				RollSession s = new RollSession(1, dice, 0, cfg.durationSeconds, false);
				s.resolve(result, 150);
				SlotMachineRenderer r = new SlotMachineRenderer();
				long total = s.totalMs();
				long[] times = {100, 700, 1500, 2000, 2350, 2500, 2700, 2900, 3200, 3350};
				StringBuilder sheetName = new StringBuilder();
				for (long t : times) {
					// advance frames at ~60 fps up to t so particle state evolves realistically
					SlotMachineRenderer rr = r;
					ImageCanvas c = new ImageCanvas(480, 270);
					c.fillBackground();
					for (long tt = Math.max(0, t - 200); tt <= t; tt += 16) {
						ImageCanvas scratch = new ImageCanvas(480, 270);
						rr.render(scratch, s, tt, cfg);
						scratch.dispose();
					}
					rr.render(c, s, t, cfg);
					c.dispose();
					BufferedImage up = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
					up.getGraphics().drawImage(c.image.getScaledInstance(960, 540, java.awt.Image.SCALE_FAST), 0, 0, null);
					String name = (dice.highClass ? "hc" : "std") + "_r" + result + "_t" + t + ".png";
					ImageIO.write(up, "png", new File(out, name));
				}
				System.out.println(dice + " result " + result + " total " + total + " ms");
			}
		}
	}
}
