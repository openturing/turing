package com.viglet.turing.service.chatmemory;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis-backed chat memory store. For each {@code conversationId} we keep:
 * <ul>
 *   <li>A HASH at {@code <prefix>:<convId>} with the headline fields
 *       ({@code siteName}, {@code agentId}, {@code locale}, {@code createdAt},
 *       {@code updatedAt}, {@code turnCount}, {@code lastUserMessage},
 *       {@code lastAssistantMessage}).</li>
 *   <li>A LIST at {@code <prefix>:<convId>:messages} with one JSON entry per
 *       message, trimmed to {@code maxMessages} via {@code LTRIM} after each
 *       batch so the key stays bounded.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
public class TurRedisChatMemoryStore implements TurChatMemoryStore {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final JedisPool pool;
    private final String keyPrefix;

    public TurRedisChatMemoryStore(String redisUri, String keyPrefix) {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(20);
        cfg.setMaxWait(Duration.ofSeconds(2));
        this.pool = new JedisPool(cfg, URI.create(redisUri));
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public TurChatMemoryEngine getEngine() {
        return TurChatMemoryEngine.REDIS;
    }

    @Override
    public void appendBatch(String conversationId, List<TurChatMemoryEvent> batch) {
        if (batch.isEmpty()) return;
        TurChatMemoryEvent first = batch.getFirst();
        TurChatMemoryEvent last = batch.getLast();
        int maxMessages = Math.max(1, last.maxMessages());

        String hashKey = keyPrefix + ":" + conversationId;
        String listKey = hashKey + ":messages";

        try (Jedis jedis = pool.getResource()) {
            Map<String, String> headline = new HashMap<>();
            headline.put("siteName", nullSafe(first.siteName()));
            headline.put("agentId", nullSafe(first.agentId()));
            headline.put("locale", nullSafe(last.locale()));
            headline.put("updatedAt", last.timestamp().toString());
            headline.put("lastUserMessage", nullSafe(last.userMessage()));
            headline.put("lastAssistantMessage", nullSafe(last.assistantMessage()));
            jedis.hset(hashKey, headline);
            jedis.hsetnx(hashKey, "createdAt", last.timestamp().toString());
            jedis.hincrBy(hashKey, "turnCount", batch.size());

            // RPUSH preserves chronological order (oldest first → newest last);
            // LTRIM keeps the tail (newest maxMessages entries).
            List<String> serialized = new ArrayList<>(batch.size() * 2);
            for (TurChatMemoryEvent e : batch) {
                serialized.add(serialize("user", e.userMessage(), e.timestamp().toString()));
                serialized.add(serialize("assistant", e.assistantMessage(), e.timestamp().toString()));
            }
            jedis.rpush(listKey, serialized.toArray(new String[0]));
            jedis.ltrim(listKey, -maxMessages, -1);
        } catch (Exception e) {
            log.warn("Failed to persist chat memory batch to Redis for conversation {}: {}",
                    conversationId, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> findMessages(String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        int cap = Math.max(1, Math.min(limit, 1000));
        String listKey = keyPrefix + ":" + conversationId + ":messages";
        try (Jedis jedis = pool.getResource()) {
            // LRANGE returns oldest → newest; -cap to -1 captures the tail.
            List<String> raw = jedis.lrange(listKey, -cap, -1);
            if (raw == null || raw.isEmpty()) return List.of();
            List<Map<String, Object>> result = new ArrayList<>(raw.size());
            for (String json : raw) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = MAPPER.readValue(json, Map.class);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("role", String.valueOf(parsed.get("role")));
                    row.put("content", String.valueOf(parsed.getOrDefault("content", "")));
                    row.put("timestamp", parsed.get("timestamp"));
                    result.add(row);
                } catch (Exception parseEx) {
                    log.debug("Skipping malformed memory entry in {}: {}", listKey, parseEx.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to read chat memory messages for conversation {}: {}",
                    conversationId, e.getMessage());
            return List.of();
        }
    }

    private static String serialize(String role, String content, String ts) {
        Map<String, String> doc = new HashMap<>();
        doc.put("role", role);
        doc.put("content", content == null ? "" : content);
        doc.put("timestamp", ts);
        try {
            return MAPPER.writeValueAsString(doc);
        } catch (Exception e) {
            return "{\"role\":\"" + role + "\",\"content\":\"\",\"timestamp\":\"" + ts + "\"}";
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
