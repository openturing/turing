package com.viglet.turing.service.chatmemory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the {@link TurChatMemoryStore} bean based on
 * {@code turing.logging.engine}. The chosen store is reused for the
 * application lifetime so the underlying client (Mongo / Jedis pool) is not
 * rebuilt per call.
 *
 * <p>Falls back to {@link TurNoOpChatMemoryStore} whenever the engine is
 * {@code none} or the matching enabled flag is off — keeps callers unaware
 * of whether persistence is actually wired.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Configuration
public class TurChatMemoryConfig {

    private final String loggingEngine;
    private final boolean mongoEnabled;
    private final String mongoUri;
    private final boolean redisEnabled;
    private final String redisUri;
    private final String databaseName;
    private final String chatCollectionName;

    private TurChatMemoryStore createdStore;

    public TurChatMemoryConfig(
            @Value("${turing.logging.engine:none}") String loggingEngine,
            @Value("${turing.mongodb.enabled:false}") boolean mongoEnabled,
            @Value("${turing.mongodb.uri:mongodb://localhost:27017}") String mongoUri,
            @Value("${turing.redis.enabled:false}") boolean redisEnabled,
            @Value("${turing.redis.uri:redis://localhost:6379/0}") String redisUri,
            @Value("${turing.logging.database:turingLog}") String databaseName,
            @Value("${turing.logging.collection.chat:chat}") String chatCollectionName) {
        this.loggingEngine = loggingEngine;
        this.mongoEnabled = mongoEnabled;
        this.mongoUri = mongoUri;
        this.redisEnabled = redisEnabled;
        this.redisUri = redisUri;
        this.databaseName = databaseName;
        this.chatCollectionName = chatCollectionName;
    }

    @Bean
    public TurChatMemoryStore turChatMemoryStore() {
        TurChatMemoryEngine engine = TurChatMemoryEngine.fromConfig(loggingEngine);
        switch (engine) {
            case MONGODB:
                if (!mongoEnabled) {
                    log.info("Chat memory: turing.logging.engine=mongodb but turing.mongodb.enabled=false — disabled");
                    createdStore = new TurNoOpChatMemoryStore();
                } else {
                    log.info("Chat memory: using MongoDB store (database={}, collection={})",
                            databaseName, chatCollectionName);
                    createdStore = new TurMongoChatMemoryStore(mongoUri, databaseName, chatCollectionName);
                }
                break;
            case REDIS:
                if (!redisEnabled) {
                    log.info("Chat memory: turing.logging.engine=redis but turing.redis.enabled=false — disabled");
                    createdStore = new TurNoOpChatMemoryStore();
                } else {
                    log.info("Chat memory: using Redis store (key prefix={})", chatCollectionName);
                    createdStore = new TurRedisChatMemoryStore(redisUri, chatCollectionName);
                }
                break;
            default:
                log.info("Chat memory: turing.logging.engine=none — disabled");
                createdStore = new TurNoOpChatMemoryStore();
        }
        return createdStore;
    }

    @PreDestroy
    public void close() {
        if (createdStore instanceof TurMongoChatMemoryStore m) {
            m.close();
        } else if (createdStore instanceof TurRedisChatMemoryStore r) {
            r.close();
        }
    }
}
