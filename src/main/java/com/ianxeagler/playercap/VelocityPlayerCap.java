package com.ianxeagler.playercap;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Plugin(
        id = "velocityplayercap",
        name = "VelocityPlayerCap",
        version = "1.0.0",
        description = "Enforces a strict hard player cap on the Velocity proxy with LuckPerms bypass support.",
        authors = {"NeverArchers"}
)
public class VelocityPlayerCap {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private int maxPlayers = 100;
    private String kickMessage = "<red>The network is currently full! (<yellow>%online%/%max%</yellow>)\n<gray>Try again shortly.";

    @Inject
    public VelocityPlayerCap(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("playercap")
                        .aliases("vcap")
                        .build(),
                new PlayerCapCommand()
        );

        logger.info("VelocityPlayerCap initialized successfully! Global hard cap set to {} players.", maxPlayers);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        int currentOnline = server.getPlayerCount();
        if (currentOnline >= maxPlayers) {
            if (player.hasPermission("velocity.cap.bypass")) {
                return;
            }

            String formatted = kickMessage
                    .replace("%online%", String.valueOf(currentOnline))
                    .replace("%max%", String.valueOf(maxPlayers));

            Component reason = miniMessage.deserialize(formatted);
            event.setResult(ResultedEvent.ComponentResult.denied(reason));
        }
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing ping = event.getPing();
        ServerPing updatedPing = ping.asBuilder()
                .maximumPlayers(maxPlayers)
                .build();
        event.setPing(updatedPing);
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configFile = dataDirectory.resolve("config.properties");
            Properties props = new Properties();

            if (!Files.exists(configFile)) {
                props.setProperty("max-players", String.valueOf(maxPlayers));
                props.setProperty("kick-message", kickMessage);
                try (OutputStream out = Files.newOutputStream(configFile)) {
                    props.store(out, "VelocityPlayerCap Configuration");
                }
            } else {
                try (InputStream in = Files.newInputStream(configFile)) {
                    props.load(in);
                }
                maxPlayers = Integer.parseInt(props.getProperty("max-players", "100"));
                kickMessage = props.getProperty("kick-message", kickMessage);
            }
        } catch (IOException | NumberFormatException e) {
            logger.error("Failed to load config.properties, using defaults.", e);
        }
    }

    private void saveConfig() {
        try {
            Path configFile = dataDirectory.resolve("config.properties");
            Properties props = new Properties();
            props.setProperty("max-players", String.valueOf(maxPlayers));
            props.setProperty("kick-message", kickMessage);
            try (OutputStream out = Files.newOutputStream(configFile)) {
                props.store(out, "VelocityPlayerCap Configuration");
            }
        } catch (IOException e) {
            logger.error("Failed to save config.properties.", e);
        }
    }

    private class PlayerCapCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                source.sendMessage(miniMessage.deserialize(
                        "<gray>Current Global Cap: <yellow>" + server.getPlayerCount() + "/" + maxPlayers +
                        "\n<gray>Usage: <white>/playercap <set <amount> | reload>"
                ));
                return;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                loadConfig();
                source.sendMessage(miniMessage.deserialize("<green>Reloaded VelocityPlayerCap! Max players: <yellow>" + maxPlayers));
                return;
            }

            if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
                try {
                    int newCap = Integer.parseInt(args[1]);
                    if (newCap < 0) {
                        source.sendMessage(miniMessage.deserialize("<red>Player cap cannot be negative."));
                        return;
                    }
                    maxPlayers = newCap;
                    saveConfig();
                    source.sendMessage(miniMessage.deserialize("<green>Global player cap updated to <yellow>" + maxPlayers + "<green>."));
                } catch (NumberFormatException e) {
                    source.sendMessage(miniMessage.deserialize("<red>Invalid number: " + args[1]));
                }
                return;
            }

            source.sendMessage(miniMessage.deserialize("<red>Usage: /playercap <set <amount> | reload>"));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("velocity.cap.admin");
        }
    }
}
