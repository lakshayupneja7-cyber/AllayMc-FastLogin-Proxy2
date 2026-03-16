package com.allaymc.fastloginproxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

@Plugin(
        id = "allaymcfastloginproxy",
        name = "AllayMcFastLoginProxy",
        version = "1.0.0",
        authors = {"AllayMc"}
)
public class AllayMcFastLoginProxy {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ProxyAuthService authService;

    private String backendServerName = "lobby";
    private String pluginChannel = "allaymc:auth";
    private String reconnectKickMessage = "Verification complete. Reconnect now.";

    @Inject
    public AllayMcFastLoginProxy(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        try {
            Files.createDirectories(dataDirectory);

            Path configPath = dataDirectory.resolve("config.properties");
            if (!Files.exists(configPath)) {
                Files.writeString(configPath, """
                        backend-server-name=lobby
                        plugin-message-channel=allaymc:auth
                        verification-window-seconds=20
                        reconnect-kick-message=Verification complete. Reconnect now.
                        mojang-timeout-ms=1500
                        """, StandardCharsets.UTF_8);
            }

            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }

            backendServerName = properties.getProperty("backend-server-name", "lobby");
            pluginChannel = properties.getProperty("plugin-message-channel", "allaymc:auth");
            reconnectKickMessage = properties.getProperty("reconnect-kick-message", "Verification complete. Reconnect now.");

            int verificationWindowSeconds = Integer.parseInt(properties.getProperty("verification-window-seconds", "20"));
            int timeoutMs = Integer.parseInt(properties.getProperty("mojang-timeout-ms", "1500"));

            ProxyDatabase database = new ProxyDatabase(dataDirectory);
            database.connect();

            MojangLookupService mojangLookupService = new MojangLookupService(timeoutMs);
            authService = new ProxyAuthService(database, mojangLookupService, verificationWindowSeconds);

            proxy.getChannelRegistrar().register(MinecraftChannelIdentifier.from(pluginChannel));
            logger.info("AllayMcFastLoginProxy enabled.");
        } catch (Exception e) {
            logger.error("Failed to initialize AllayMcFastLoginProxy", e);
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        if (authService.needsFirstJoinVerification(player.getUsername())) {
            player.disconnect(Component.text(reconnectKickMessage));
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();

        AuthMode mode = authService.resolveMode(player.getUsername(), player.getUniqueId());
        if (mode == null) {
            return;
        }

        Optional<RegisteredServer> currentServer = player.getCurrentServer().map(conn -> conn.getServer());
        if (currentServer.isEmpty()) {
            return;
        }

        String payload = "AUTH|" + player.getUsername() + "|" + player.getUniqueId() + "|" + mode.name();

        currentServer.get().sendPluginMessage(
                MinecraftChannelIdentifier.from(pluginChannel),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        authService.clear(event.getPlayer().getUniqueId());
    }
}
