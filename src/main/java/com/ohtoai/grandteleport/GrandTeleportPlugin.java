package com.ohtoai.grandteleport;

import com.ohtoai.grandteleport.command.GtpCommand;
import com.ohtoai.grandteleport.command.GtpTabCompleter;
import com.ohtoai.grandteleport.config.ConfigManager;
import com.ohtoai.grandteleport.config.TransitionSettings;
import com.ohtoai.grandteleport.listener.ModHandshakeListener;
import com.ohtoai.grandteleport.listener.PlayerFreezeListener;
import com.ohtoai.grandteleport.network.CameraPackets;
import com.ohtoai.grandteleport.teleport.CommandInterceptor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GrandTeleportPlugin extends JavaPlugin implements PluginMessageListener {

    private static GrandTeleportPlugin instance;

    // Config
    private ConfigManager configManager;

    // Core components
    private CommandInterceptor commandInterceptor;

    // Mod-enabled player tracking
    private final Set<UUID> modEnabledPlayers = ConcurrentHashMap.newKeySet();

    // Active transitions (prevent concurrent transitions for same player)
    private final Set<UUID> activeTransitions = ConcurrentHashMap.newKeySet();

    // Bypass flag — set before re-dispatching a command to avoid double interception
    private final Set<UUID> bypassNextCommand = ConcurrentHashMap.newKeySet();

    public static GrandTeleportPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("  ____                     _ _____      _                 _   ");
        getLogger().info(" / ___|_ __ __ _ _ __   __| |_   _|_ __| | ___  _ __ ___ | |_ ");
        getLogger().info("| |  _| '__/ _` | '_ \\ / _` | | | | '__| |/ _ \\| '_ ` _ \\| __|");
        getLogger().info("| |_| | | | (_| | | | | (_| | |_| | |  | | (_) | | | | | | |_ ");
        getLogger().info(" \\____|_|  \\__,_|_| |_|\\__,_|\\__,_|_|  |_|\\___/|_| |_| |_|\\__|");
        getLogger().info("");
        getLogger().info("  GrandTeleportPlugin v" + getPluginMeta().getVersion() + " — Ready");

        // 1. Load config
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            getLogger().info("Configuration loaded.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load config!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Register plugin messaging channels for client mod communication
        if (configManager.getConfig().getBoolean("mod.enabled", true)) {
            for (String ch : CameraPackets.ALL_CHANNELS) {
                getServer().getMessenger().registerIncomingPluginChannel(this, ch, this);
                getServer().getMessenger().registerOutgoingPluginChannel(this, ch);
                getLogger().info("Plugin channel registered: " + ch);
            }
        }

        // 3. Initialize command interceptor
        commandInterceptor = new CommandInterceptor(this);
        getServer().getPluginManager().registerEvents(commandInterceptor, this);
        getLogger().info("Command interceptor registered.");

        // 4. Register freeze listener
        getServer().getPluginManager().registerEvents(new PlayerFreezeListener(this), this);
        getLogger().info("Player freeze listener registered.");

        // 5. Register mod handshake listener
        getServer().getPluginManager().registerEvents(new ModHandshakeListener(this), this);
        getLogger().info("Mod handshake listener registered.");

        // 6. Register commands
        GtpCommand gtpCommand = new GtpCommand(this);
        Objects.requireNonNull(getCommand("grandtp")).setExecutor(gtpCommand);
        Objects.requireNonNull(getCommand("grandtp")).setTabCompleter(new GtpTabCompleter());
        getLogger().info("Commands registered: /grandtp, /gtp");

        getLogger().info("GrandTeleportPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Unregister all plugin channels
        for (String ch : CameraPackets.ALL_CHANNELS) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, ch);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, ch);
        }

        activeTransitions.clear();
        bypassNextCommand.clear();
        modEnabledPlayers.clear();

        getLogger().info("GrandTeleportPlugin disabled.");
    }

    // --- PluginMessageListener ---

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (message.length == 0) return;

        if (CameraPackets.CHANNEL_CLIENT_HELLO.equals(channel)) {
            modEnabledPlayers.add(player.getUniqueId());
            getLogger().info("Client mod detected: " + player.getName());
            boolean modEnabled = configManager.getConfig().getBoolean("mod.enabled", true);
            CameraPackets.sendServerHello(player, modEnabled);
        } else if (CameraPackets.CHANNEL_CAMERA_READY.equals(channel)) {
            getLogger().fine("CameraReady received from " + player.getName());
        }
    }

    // --- Public accessors ---

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TransitionSettings getTransitionSettings() {
        return TransitionSettings.fromConfig(configManager.getConfig());
    }

    public CommandInterceptor getCommandInterceptor() {
        return commandInterceptor;
    }

    // --- Mod player tracking ---

    public boolean hasMod(Player player) {
        return modEnabledPlayers.contains(player.getUniqueId());
    }

    public void removeModPlayer(UUID uuid) {
        modEnabledPlayers.remove(uuid);
    }

    public int getModPlayerCount() {
        return modEnabledPlayers.size();
    }

    // --- Active transition tracking ---

    public boolean isInTransition(Player player) {
        return activeTransitions.contains(player.getUniqueId());
    }

    public boolean startTransition(Player player) {
        return activeTransitions.add(player.getUniqueId());
    }

    public void endTransition(Player player) {
        activeTransitions.remove(player.getUniqueId());
    }

    public int getActiveTransitionCount() {
        return activeTransitions.size();
    }

    // --- Bypass command tracking ---

    public boolean shouldBypassNextCommand(Player player) {
        return bypassNextCommand.remove(player.getUniqueId());
    }

    public void setBypassNextCommand(Player player) {
        bypassNextCommand.add(player.getUniqueId());
    }

    // --- Freeze tracking ---

    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    public void freezePlayer(Player player) {
        frozenPlayers.add(player.getUniqueId());
    }

    public void unfreezePlayer(Player player) {
        frozenPlayers.remove(player.getUniqueId());
    }
}
