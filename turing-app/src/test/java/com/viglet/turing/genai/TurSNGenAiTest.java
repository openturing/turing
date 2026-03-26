package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * Tests for TurSNGenAi.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSNGenAiTest {

    @Mock
    private TurSNSearchProcess turSNSearchProcess;
    @Mock
    private TurGenAiContextFactory contextFactory;
    @Mock
    private TurSNSiteLocaleRepository localeRepository;

    private TurSNGenAi turSNGenAi;

    @BeforeEach
    void setUp() {
        turSNGenAi = new TurSNGenAi(turSNSearchProcess, contextFactory, localeRepository);
    }

    @Test
    void shouldReturnDisabledMessageWhenContextIsDisabled() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(false);

        TurChatMessage result = turSNGenAi.assistant(context, "any question");

        assertFalse(result.isEnabled());
        assertEquals("AI configuration is not enabled", result.getText());
        verifyNoInteractions(turSNSearchProcess);
    }

    @Test
    void shouldReturnEnabledMessageWithNullTextForWildcardSingleToken() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        TurChatMessage result = turSNGenAi.assistant(context, "*");

        assertTrue(result.isEnabled());
        assertNull(result.getText());
        verifyNoInteractions(turSNSearchProcess);
    }

    @Test
    void shouldReturnEnabledMessageWithNullTextForWildcardContainingToken() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        TurChatMessage result = turSNGenAi.assistant(context, "test*");

        assertTrue(result.isEnabled());
        assertNull(result.getText());
    }

    @Test
    void shouldWrapSingleTokenInWhatIsQuestion() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        VectorStore vectorStore = mock(VectorStore.class);
        when(context.getVectorStore()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(context.getSystemPrompt()).thenReturn(null);

        ChatModel chatModel = mock(ChatModel.class);
        when(context.getChatModel()).thenReturn(chatModel);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("Java is a programming language");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        TurChatMessage result = turSNGenAi.assistant(context, "Java");

        assertTrue(result.isEnabled());
        assertNotNull(result.getText());
    }

    @Test
    void shouldUseDefaultPromptWhenSystemPromptIsNull() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        VectorStore vectorStore = mock(VectorStore.class);
        when(context.getVectorStore()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("relevant info")));
        when(context.getSystemPrompt()).thenReturn(null);

        ChatModel chatModel = mock(ChatModel.class);
        when(context.getChatModel()).thenReturn(chatModel);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("Answer based on info");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        TurChatMessage result = turSNGenAi.assistant(context, "what is AI?");

        assertTrue(result.isEnabled());
        assertEquals("Answer based on info", result.getText());
    }

    @Test
    void shouldUseDefaultPromptWhenSystemPromptIsBlank() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        VectorStore vectorStore = mock(VectorStore.class);
        when(context.getVectorStore()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(context.getSystemPrompt()).thenReturn("   ");

        ChatModel chatModel = mock(ChatModel.class);
        when(context.getChatModel()).thenReturn(chatModel);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("response");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        TurChatMessage result = turSNGenAi.assistant(context, "multi token query");

        assertTrue(result.isEnabled());
        assertEquals("response", result.getText());
    }

    @Test
    void shouldUseCustomSystemPrompt() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        VectorStore vectorStore = mock(VectorStore.class);
        when(context.getVectorStore()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(context.getSystemPrompt()).thenReturn("Custom prompt: {question} with {information}");

        ChatModel chatModel = mock(ChatModel.class);
        when(context.getChatModel()).thenReturn(chatModel);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("custom response");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        TurChatMessage result = turSNGenAi.assistant(context, "multi token query");

        assertTrue(result.isEnabled());
        assertEquals("custom response", result.getText());
    }

    @Test
    void shouldConcatenateMultipleDocumentsAsInformation() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        VectorStore vectorStore = mock(VectorStore.class);
        when(context.getVectorStore()).thenReturn(vectorStore);
        List<Document> docs = List.of(
                new Document("First document"),
                new Document("Second document"),
                new Document("Third document"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);
        when(context.getSystemPrompt()).thenReturn(null);

        ChatModel chatModel = mock(ChatModel.class);
        when(context.getChatModel()).thenReturn(chatModel);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("combined answer");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        TurChatMessage result = turSNGenAi.assistant(context, "complex question here");

        assertTrue(result.isEnabled());
        assertEquals("combined answer", result.getText());
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldHandleEmptyRelevantDocuments() {
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        VectorStore vectorStore = mock(VectorStore.class);
        when(context.getVectorStore()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        when(context.getSystemPrompt()).thenReturn(null);

        ChatModel chatModel = mock(ChatModel.class);
        when(context.getChatModel()).thenReturn(chatModel);

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("no info available");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        TurChatMessage result = turSNGenAi.assistant(context, "query without docs");

        assertTrue(result.isEnabled());
        assertEquals("no info available", result.getText());
    }

    @Test
    void addDocuments_shouldSkipWhenSiteNotFound() {
        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("non-existent-site"));

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        when(turSNSearchProcess.getSNSite("non-existent-site")).thenReturn(Optional.empty());

        turSNGenAi.addDocuments(jobItems);

        verifyNoInteractions(contextFactory);
    }

    @Test
    void addDocuments_shouldSkipWhenGenAiIsNull() {
        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(null);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));

        turSNGenAi.addDocuments(jobItems);

        verifyNoInteractions(contextFactory);
    }

    @Test
    void addDocuments_shouldSkipWhenGenAiDisabled() {
        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(false);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));

        turSNGenAi.addDocuments(jobItems);

        verifyNoInteractions(contextFactory);
    }

    @Test
    void addDocuments_shouldSkipWhenContextIsDisabled() {
        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));
        jobItem.setLocale(Locale.ENGLISH);

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(true);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));

        TurGenAiContext disabledContext = TurGenAiContext.disabled();
        when(contextFactory.build(eq(genAi), any())).thenReturn(disabledContext);

        turSNGenAi.addDocuments(jobItems);

        // Context is disabled, so vectorStore.add should not be called
    }

    @Test
    void addDocuments_shouldAddDocumentWhenContextEnabled() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "doc-1");
        attrs.put("source_apps", "test-provider");
        attrs.put("title", "Test Title");
        attrs.put("abstract", "Test Abstract");
        attrs.put("text", "Test text content");
        attrs.put("url", "http://example.com");
        attrs.put("modification_date", "2026-01-01");
        attrs.put("publication_date", "2026-01-01");

        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));
        jobItem.setLocale(Locale.ENGLISH);
        jobItem.setAttributes(attrs);

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(true);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));

        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiContext context = TurGenAiContext.builder()
                .enabled(true)
                .vectorStore(vectorStore)
                .build();
        when(contextFactory.build(eq(genAi), any())).thenReturn(context);

        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setCore("en_core");
        when(localeRepository.findByTurSNSiteAndLanguage(site, Locale.ENGLISH)).thenReturn(siteLocale);

        turSNGenAi.addDocuments(jobItems);

        verify(vectorStore).add(any(List.class));
    }

    @Test
    void addDocuments_shouldUseNullCollectionWhenLocaleIsNull() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "doc-2");
        attrs.put("source_apps", "provider");
        attrs.put("title", "Title");

        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));
        jobItem.setLocale(null);
        jobItem.setAttributes(attrs);

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(true);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));

        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiContext context = TurGenAiContext.builder()
                .enabled(true)
                .vectorStore(vectorStore)
                .build();
        when(contextFactory.build(eq(genAi), eq(null))).thenReturn(context);

        turSNGenAi.addDocuments(jobItems);

        verify(vectorStore).add(any(List.class));
    }

    @Test
    void addDocuments_shouldUseNullCollectionWhenSiteLocaleNotFound() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "doc-3");
        attrs.put("source_apps", "provider");
        attrs.put("title", "Titre");

        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));
        jobItem.setLocale(Locale.FRENCH);
        jobItem.setAttributes(attrs);

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(true);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));
        when(localeRepository.findByTurSNSiteAndLanguage(site, Locale.FRENCH)).thenReturn(null);

        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiContext context = TurGenAiContext.builder()
                .enabled(true)
                .vectorStore(vectorStore)
                .build();
        when(contextFactory.build(eq(genAi), eq(null))).thenReturn(context);

        turSNGenAi.addDocuments(jobItems);

        verify(vectorStore).add(any(List.class));
    }

    @Test
    void addDocuments_shouldOnlyAddAllowedAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "doc-4");
        attrs.put("source_apps", "provider");
        attrs.put("title", "My Title");
        attrs.put("abstract", "My Abstract");
        attrs.put("text", "My Text");
        attrs.put("customField", "Should not be included");
        attrs.put("url", "http://example.com");

        TurSNJobItem jobItem = new TurSNJobItem();
        jobItem.setSiteNames(List.of("test-site"));
        jobItem.setLocale(Locale.ENGLISH);
        jobItem.setAttributes(attrs);

        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of(jobItem));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(true);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(turSNSearchProcess.getSNSite("test-site")).thenReturn(Optional.of(site));
        when(localeRepository.findByTurSNSiteAndLanguage(site, Locale.ENGLISH)).thenReturn(null);

        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiContext context = TurGenAiContext.builder()
                .enabled(true)
                .vectorStore(vectorStore)
                .build();
        when(contextFactory.build(eq(genAi), eq(null))).thenReturn(context);

        turSNGenAi.addDocuments(jobItems);

        verify(vectorStore).add(any(List.class));
    }

    @Test
    void addDocuments_emptyJobItems() {
        TurSNJobItems jobItems = new TurSNJobItems();
        jobItems.setTuringDocuments(List.of());

        turSNGenAi.addDocuments(jobItems);

        verifyNoInteractions(turSNSearchProcess);
        verifyNoInteractions(contextFactory);
    }

    @Test
    void testConstants() {
        assertEquals("source_id", TurSNGenAi.SOURCE_ID);
        assertEquals("sites", TurSNGenAi.SITES);
        assertEquals("locale", TurSNGenAi.LOCALE);
        assertEquals("question", TurSNGenAi.QUESTION);
        assertEquals("information", TurSNGenAi.INFORMATION);
        assertNotNull(TurSNGenAi.DEFAULT_PROMPT);
        assertTrue(TurSNGenAi.DEFAULT_PROMPT.contains("{question}"));
        assertTrue(TurSNGenAi.DEFAULT_PROMPT.contains("{information}"));
    }
}
