package com.viglet.turing.service.chatmemory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import lombok.extern.slf4j.Slf4j;

/**
 * MongoDB-backed chat memory store. One document per {@code conversationId}
 * is kept in the configured collection; each appended batch advances the
 * {@code turnCount} counter and pushes the new turns into the {@code messages}
 * array, capped via {@code $push.$slice} so old turns are dropped beyond
 * {@code maxMessages}.
 *
 * <p>The {@link MongoClient} is reused for the bean's lifetime to avoid the
 * per-call connection cost of {@link MongoClients#create(String)}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
public class TurMongoChatMemoryStore implements TurChatMemoryStore {

    private final MongoClient client;
    private final String databaseName;
    private final String collectionName;

    public TurMongoChatMemoryStore(String mongoUri, String databaseName, String collectionName) {
        this.client = MongoClients.create(mongoUri);
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public TurChatMemoryEngine getEngine() {
        return TurChatMemoryEngine.MONGODB;
    }

    @Override
    public void appendBatch(String conversationId, List<TurChatMemoryEvent> batch) {
        if (batch.isEmpty()) return;
        try {
            MongoDatabase database = client.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);

            TurChatMemoryEvent first = batch.getFirst();
            TurChatMemoryEvent last = batch.getLast();
            Date now = Date.from(last.timestamp());
            int maxMessages = Math.max(1, last.maxMessages());

            List<Document> messageDocs = new ArrayList<>(batch.size() * 2);
            for (TurChatMemoryEvent e : batch) {
                Date ts = Date.from(e.timestamp());
                messageDocs.add(toMessageDoc("user", e.userMessage(), ts));
                messageDocs.add(toMessageDoc("assistant", e.assistantMessage(), ts));
            }

            Bson update = Updates.combine(
                    Updates.setOnInsert("createdAt", now),
                    Updates.setOnInsert("siteName", first.siteName()),
                    Updates.setOnInsert("agentId", first.agentId()),
                    Updates.set("locale", last.locale()),
                    Updates.set("updatedAt", now),
                    Updates.set("lastUserMessage", last.userMessage()),
                    Updates.set("lastAssistantMessage", last.assistantMessage()),
                    Updates.inc("turnCount", batch.size()),
                    Updates.pushEach(
                            "messages",
                            messageDocs,
                            new PushOptions().slice(-maxMessages)));

            collection.updateOne(
                    Filters.eq("_id", conversationId),
                    update,
                    new UpdateOptions().upsert(true));
        } catch (Exception e) {
            log.warn("Failed to persist chat memory batch for conversation {}: {}",
                    conversationId, e.getMessage());
        }
    }

    private static Document toMessageDoc(String role, String content, Date ts) {
        return new Document()
                .append("role", role)
                .append("content", content == null ? "" : content)
                .append("timestamp", ts);
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
