package me.playbosswar.tea.core.impl.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Sends run on one background thread, in order; a 429 is retried once after the
 * delay Discord asks for. Webhook URLs are secrets and are never logged.
 */
public final class DiscordWebhooks {

    public enum Event {
        MATCH_RESULTS("match-results", 0x52A554),
        STAFF_ACTIONS("staff-actions", 0xD9534F),
        ACHIEVEMENTS("achievements", 0xF0AD4E),
        SERVER_STATUS("server-status", 0x5BC0DE);

        final String key;
        final int color;

        Event(String key, int color) {
            this.key = key;
            this.color = color;
        }
    }

    public record Field(String name, String value, boolean inline) { }

    private final Plugin plugin;
    private final Logger logger;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeaCore-discord");
        t.setDaemon(true);
        return t;
    });

    public DiscordWebhooks(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public boolean enabled(Event event) {
        return !url(event).isEmpty();
    }

    private String url(Event event) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("discord." + event.key);
        String url = s == null ? "" : s.getString("url", "");
        return url == null ? "" : url.trim();
    }

    public void post(Event event, String title, String description, List<Field> fields) {
        String url = url(event);
        if (url.isEmpty()) {
            return;
        }
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", limit(title, 256));
        if (description != null && !description.isBlank()) {
            embed.put("description", limit(description, 4000));
        }
        embed.put("color", event.color);
        embed.put("timestamp", Instant.now().toString());
        List<Map<String, Object>> f = new ArrayList<>();
        for (Field field : fields == null ? List.<Field>of() : fields) {
            if (f.size() == 25) {
                break;
            }
            f.add(Map.of("name", limit(field.name(), 256), "value", limit(field.value().isBlank() ? "-" : field.value(), 1024),
                    "inline", field.inline()));
        }
        if (!f.isEmpty()) {
            embed.put("fields", f);
        }
        embed.put("footer", Map.of("text", "TeaCore - " + plugin.getServer().getName()));
        Map<String, Object> body = Map.of("embeds", List.of(embed), "allowed_mentions", Map.of("parse", List.of()));
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not build Discord message", e);
            return;
        }
        try {
            sender.execute(() -> send(event, url, json, true));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void send(Event event, String url, String json, boolean retry) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "TeaCore")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 && retry) {
                double wait = 1.0;
                try {
                    wait = mapper.readTree(response.body()).path("retry_after").asDouble(1.0);
                } catch (Exception ignored) {
                }
                Thread.sleep((long) (Math.min(wait, 30) * 1000));
                send(event, url, json, false);
            } else if (response.statusCode() >= 300) {
                logger.warning("Discord webhook for " + event.key + " answered " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.warning("Could not reach the Discord webhook for " + event.key + " (" + e.getMessage() + ")");
        }
    }

    /** Flushes the queue for up to 5 s before stopping. */
    public void close() {
        sender.shutdown();
        try {
            sender.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String limit(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
