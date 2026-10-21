package com.rngaddons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.rngaddons.features.archfiend.core.Settings;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads/saves config/rngaddons.json. Missing file, missing keys or a corrupt file never break startup.
 * On first start it takes over the settings of the old "Archfiend Slots" mod (config/archfiendslots.json).
 */
public final class ConfigManager {
	private static final Logger LOG = LoggerFactory.getLogger("rngaddons/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigManager() {}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("rngaddons.json");
	}

	private static Path legacyPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("archfiendslots.json");
	}

	public static ModConfig load() {
		Path p = path();
		if (!Files.exists(p)) {
			ModConfig fresh = new ModConfig();
			migrateLegacy(fresh);
			save(fresh);
			return fresh;
		}
		try {
			String json = Files.readString(p, StandardCharsets.UTF_8);
			ModConfig loaded = GSON.fromJson(json, ModConfig.class);
			if (loaded == null) loaded = new ModConfig();
			return loaded.sanitize(); // missing keys keep their defaults, out-of-range values are clamped
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("Could not read {} ({}); using defaults and keeping the broken file as .broken", p, e.toString());
			try {
				Files.move(p, p.resolveSibling("rngaddons.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more to do
			}
			ModConfig fresh = new ModConfig();
			save(fresh);
			return fresh;
		}
	}

	/** Old single-feature config of "Archfiend Slots" 1.0.0 -> section archfiendDice. */
	private static void migrateLegacy(ModConfig target) {
		Path old = legacyPath();
		if (!Files.exists(old)) return;
		try {
			Settings s = GSON.fromJson(Files.readString(old, StandardCharsets.UTF_8), Settings.class);
			if (s != null) {
				target.archfiendDice = s.sanitize();
				LOG.info("Took over the settings from {}", old.getFileName());
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOG.warn("Old config {} could not be read ({}); using defaults", old.getFileName(), e.toString());
		}
	}

	public static void save(ModConfig c) {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(c), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.warn("Could not write config: {}", e.toString());
		}
	}
}
