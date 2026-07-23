package com.ohtoai.grandteleport.config;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Manages config.yml loading, saving, and reloading.
 * Merges missing keys from the jar's default config.yml.
 */
public class ConfigManager {

    private final GrandTeleportPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    public ConfigManager(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load config.yml from disk, creating from jar default if missing.
     * Merges any new keys from the default config into the existing file.
     */
    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Create default config if not exists
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Merge with defaults from jar
        try (InputStream defaultStream = plugin.getResource("config.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                config.setDefaults(defaultConfig);

                // Save any missing keys
                boolean needsSave = false;
                for (String key : defaultConfig.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defaultConfig.get(key));
                        needsSave = true;
                    }
                }
                if (needsSave) {
                    save();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not merge default config", e);
        }
    }

    /**
     * Reload from disk. Used by /gtp reload.
     */
    public void reload() {
        if (configFile == null) {
            load();
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Re-merge defaults
        try (InputStream defaultStream = plugin.getResource("config.yml")) {
            if (defaultStream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not merge default config during reload", e);
        }
    }

    /**
     * Save current config to disk.
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml!", e);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public File getConfigFile() {
        return configFile;
    }
}
