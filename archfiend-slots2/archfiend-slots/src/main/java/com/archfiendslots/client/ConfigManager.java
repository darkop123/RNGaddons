package com.archfiendslots.client;

import com.archfiendslots.core.Settings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads/saves config/archfiendslots.json. Missing file, missing keys or a corrupt file never break startup. */
final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigManager() {}

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("archfiendslots.json");
	}

	static Settings load() {
		Path p = path();
		if (!Files.exists(p)) {
			Settings fresh = new Settings();
			save(fresh);
			return fresh;
		}
		try {
			String json = Files.readString(p, StandardCharsets.UTF_8);
			Settings loaded = GSON.fromJson(json, Settings.class);
			if (loaded == null) loaded = new Settings();
			return loaded.sanitize(); // missing keys keep their defaults, out-of-range values are clamped
		} catch (IOException | JsonParseException | IllegalStateException e) {
			ArchfiendSlotsClient.LOG.warn("Could not read {} ({}); using defaults and keeping the broken file as .broken", p, e.toString());
			try {
				Files.move(p, p.resolveSibling("archfiendslots.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more to do
			}
			Settings fresh = new Settings();
			save(fresh);
			return fresh;
		}
	}

	static void save(Settings s) {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(s), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ArchfiendSlotsClient.LOG.warn("Could not write config: {}", e.toString());
		}
	}
}
