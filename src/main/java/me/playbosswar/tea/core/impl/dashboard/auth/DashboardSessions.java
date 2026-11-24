package me.playbosswar.tea.core.impl.dashboard.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Held in memory, so a restart signs everyone out. The browser holds the token in an
 * HttpOnly cookie; {@link Session#id()} is a separate short id that is safe to show.
 */
public final class DashboardSessions {

    public static final Duration LIFETIME = Duration.ofHours(12);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> byToken = new ConcurrentHashMap<>();

    /**
     * @param uuid {@code null} for the admin-token login.
     */
    public record Session(String id, String token, UUID uuid, String name, Set<String> permissions,
                          Instant created, Instant expires, String address) {

        public boolean can(String permission) {
            return permissions.contains(permission);
        }
    }

    public Session create(UUID uuid, String name, Set<String> permissions, String address) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        Session s = new Session(id, token, uuid, name, Set.copyOf(permissions), now, now.plus(LIFETIME), address);
        byToken.put(token, s);
        return s;
    }

    public Optional<Session> get(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Session s = byToken.get(token);
        if (s == null) {
            return Optional.empty();
        }
        if (s.expires().isBefore(Instant.now())) {
            byToken.remove(token);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void remove(String token) {
        if (token != null) {
            byToken.remove(token);
        }
    }

    public boolean revoke(String id) {
        return byToken.values().removeIf(s -> s.id().equals(id));
    }

    public List<Session> list() {
        Instant now = Instant.now();
        byToken.values().removeIf(s -> s.expires().isBefore(now));
        List<Session> out = new ArrayList<>(byToken.values());
        out.sort(Comparator.comparing(Session::created).reversed());
        return out;
    }
}
