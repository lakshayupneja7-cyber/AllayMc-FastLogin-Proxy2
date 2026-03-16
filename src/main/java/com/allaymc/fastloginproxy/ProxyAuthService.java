package com.allaymc.fastloginproxy;

import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyAuthService {

    private final ProxyDatabase database;
    private final MojangLookupService mojangLookupService;
    private final int verificationWindowSeconds;

    private final Map<String, Long> verificationMap = new ConcurrentHashMap<>();
    private final Map<UUID, AuthMode> resolvedModes = new ConcurrentHashMap<>();

    public ProxyAuthService(ProxyDatabase database, MojangLookupService mojangLookupService, int verificationWindowSeconds) {
        this.database = database;
        this.mojangLookupService = mojangLookupService;
        this.verificationWindowSeconds = verificationWindowSeconds;
    }

    public boolean needsFirstJoinVerification(String username) {
        long now = System.currentTimeMillis();
        Long firstSeen = verificationMap.get(username);

        if (firstSeen == null || (now - firstSeen) > verificationWindowSeconds * 1000L) {
            verificationMap.put(username, now);
            return true;
        }

        return false;
    }

    public AuthMode resolveMode(String username, UUID uuid) {
        long now = System.currentTimeMillis();
        Long firstSeen = verificationMap.get(username);

        if (firstSeen == null || (now - firstSeen) > verificationWindowSeconds * 1000L) {
            return null;
        }

        verificationMap.remove(username);

        AuthMode mode = mojangLookupService.isPremiumName(username) ? AuthMode.PREMIUM : AuthMode.CRACKED;
        resolvedModes.put(uuid, mode);

        if (mode == AuthMode.PREMIUM) {
            savePremium(username);
        }

        return mode;
    }

    public AuthMode getResolvedMode(UUID uuid) {
        return resolvedModes.get(uuid);
    }

    public void clear(UUID uuid) {
        resolvedModes.remove(uuid);
    }

    private void savePremium(String username) {
        try (PreparedStatement ps = database.getConnection().prepareStatement("""
                INSERT INTO premium_profiles(username, premium, last_verified_at)
                VALUES (?, 1, ?)
                ON CONFLICT(username) DO UPDATE SET
                    premium = 1,
                    last_verified_at = excluded.last_verified_at
                """)) {
            ps.setString(1, username);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
