package com.allaymc.fastloginproxy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ProxyDatabase {

    private final Path dataFolder;
    private Connection connection;

    public ProxyDatabase(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void connect() throws Exception {

        // FORCE LOAD SQLITE DRIVER
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new Exception("SQLite driver not found inside plugin jar!", e);
        }

        String url = "jdbc:sqlite:" + dataFolder.resolve("proxy-auth.db");

        connection = DriverManager.getConnection(url);

        Statement stmt = connection.createStatement();

        stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS premium_players (
            username TEXT PRIMARY KEY,
            uuid TEXT,
            premium INTEGER
        )
        """);

        stmt.close();
    }

    public Connection getConnection() {
        return connection;
    }
}
