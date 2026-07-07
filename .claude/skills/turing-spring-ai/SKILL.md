---
name: turing-spring-ai
description: Turing's Spring AI (v2.0.0-M2) LLM integration — the pluggable TurGenAiLlmProvider architecture and factory, supported providers and their chat/embedding capability matrix, SSE streaming chat (Flux backend + fetch/ReadableStream frontend), multimodal file attachments via the Media API, Spring AI message types, tool calling for Semantic Navigation chat, and encrypted API-key management. Use whenever working with LLM providers, chat/streaming endpoints, embeddings, multimodal chat, or Spring AI tool calling.
---

# Spring AI Integration (v2.0.0-M2)

## LLM Provider Architecture

Turing uses a pluggable provider pattern for LLM integration via Spring AI:

- **Interface**: `TurGenAiLlmProvider` — defines `getPluginType()`, `createChatModel()`, `createEmbeddingModel()`
- **Factory**: `TurGenAiLlmProviderFactory` — resolves the correct provider based on the vendor's plugin type
- **Supported providers**: OpenAI, Ollama, Anthropic (Claude), Google Gemini (native GenAI SDK), Google Gemini (OpenAI-compatible), Azure OpenAI (Copilot)

## Streaming Chat API

The chat endpoint supports **Server-Sent Events (SSE)** for token-by-token streaming:

```java
// Backend: Flux-based SSE streaming
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ChatResponse> chat(...) {
    return chatModel.stream(prompt)
        .map(response -> new ChatResponse("assistant", response.getResult().getOutput().getText()))
        .filter(r -> !r.content().isEmpty());
}
```

```typescript
// Frontend: fetch + ReadableStream for SSE consumption
const response = await fetch(url, {
  method: "POST",
  headers,
  credentials: "include",
  body,
});
const reader = response.body.getReader();
// Parse "data:" lines, extract JSON, call onToken(parsed.content)
```

## Multimodal Support (File Attachments)

The chat API accepts file uploads via multipart form data, using Spring AI's `Media` API:

```java
// Build UserMessage with media attachments
UserMessage.builder()
    .text("Analyze this document")
    .media(Media.builder()
        .mimeType(MimeType.valueOf("application/pdf"))
        .data(new ByteArrayResource(fileBytes))
        .name("document.pdf")
        .build())
    .build();
```

**Supported formats**: Images (PNG, JPEG, GIF, WebP), Documents (PDF, CSV, DOC/DOCX, XLS/XLSX, HTML, TXT, Markdown), Video formats.

## Spring AI Message Types

- `UserMessage` — supports text + media attachments via builder
- `AssistantMessage` — text + tool calls + media
- `SystemMessage` — system prompts
- `Prompt` — accepts `List<Message>` for proper conversation history

## Provider-Specific Notes

| Provider        | Chat | Embedding | Notes                                                                                           |
| --------------- | ---- | --------- | ----------------------------------------------------------------------------------------------- |
| OpenAI          | Yes  | Yes       | Full support                                                                                    |
| Ollama          | Yes  | Yes       | Local models                                                                                    |
| Anthropic       | Yes  | No        | No embedding API — throws `UnsupportedOperationException`                                       |
| Gemini (native) | Yes  | Yes       | Native chat/streaming + server-side tools via `com.google.genai.Client` (T489–T494); embeddings via `embedContent` with asymmetric task types + Matryoshka dims (T495); plugin type `gemini` |
| Gemini (OpenAI) | Yes  | No        | Uses OpenAI-compat endpoint at `generativelanguage.googleapis.com`; plugin type `gemini-openai` |
| Azure OpenAI    | Yes  | Yes       | `AzureOpenAiEmbeddingModel` uses constructor (no builder)                                       |

> Note: `AzureOpenAiEmbeddingModel` has no builder — use the constructor. Anthropic
> does not support embedding (throws `UnsupportedOperationException`). Gemini **does**
> embed via `embedContent` with asymmetric task types (RETRIEVAL_DOCUMENT at index /
> RETRIEVAL_QUERY at query) + optional Matryoshka `outputDimensionality`.

## Tool Calling (Semantic Navigation Chat)

The Semantic Navigation chat uses Spring AI's tool calling with `ToolCallingChatOptions`:

```java
// Define tools via @Tool annotation in a service
@Tool(name = "search_site", description = "...")
public String searchSite(String siteName, String locale, String query, ...) { ... }

// Use MethodToolCallbackProvider to create callbacks
ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
    .toolObjects(toolService).build().getToolCallbacks();

// Pass tools via DefaultToolCallingChatOptions with internal execution
var options = DefaultToolCallingChatOptions.builder()
    .toolCallbacks(callbacks)
    .internalToolExecutionEnabled(true)
    .build();

Prompt prompt = new Prompt(messages, options);
chatModel.stream(prompt); // Model automatically executes tools
```

- `internalToolExecutionEnabled(true)` — lets the ChatModel handle the tool call loop internally
- Tools: `list_sites`, `get_site_fields`, `search_site` (mirrors Turing MCP Server tools)
- Endpoint: `/api/v2/llm/{id}/semantic-chat`
- Always route `ToolCallback[]` through `TurToolCallbackPipeline.decorate(...)` before passing callbacks to a `ChatModel`. `@Tool` annotations may carry placeholder descriptions; real tool descriptions live under `prompts/tools/**/*.md`.
- Avoid structured-output guardrails with `gpt-4o-mini`; it has historically truncated JSON, dropped commas, or mixed fences even with schema guidance. Prefer provider tool calling with schema, or the bilingual `LlmJudge` path.

## API Key Management

- API keys stored encrypted via `TurSecretCryptoService`
- Decrypted at runtime when creating provider instances
- Provider options (JSON) stored in `TurLLMInstance.providerOptionsJson`
