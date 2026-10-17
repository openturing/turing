# Turing: Platform Overview & Reference

> Extracted from `agents.md` on 2026-06-16 to keep the per-turn agent-instructions
> file lean. This is human/reference material: platform overview, AI-agent
> capabilities, SDK usage examples, deployment, research positioning. It is **not**
> per-turn coding guidance; the actionable engineering conventions stay in
> [`agents.md`](../../agents.md).

---

## Overview

Viglet Turing is a comprehensive **Enterprise Search Intelligence Platform** that serves as an ideal foundation for AI agent systems focused on enterprise search and knowledge discovery. By combining semantic navigation, generative AI capabilities, and advanced search technologies, Turing provides the infrastructure needed for intelligent agents to interact with and understand enterprise content at scale.

## Why Turing for AI Agent Research?

### 1. **Semantic Understanding Foundation**

- **Semantic Navigation**: Advanced content understanding beyond keyword matching
- **Contextual Search**: AI agents can leverage contextual relationships between documents
- **Multi-language Support**: Global enterprise content accessibility
- **Intelligent Indexing**: Automatic content classification and relationship mapping

### 2. **Generative AI Integration**

- **LangChain4j Integration**: Built-in support for modern AI frameworks
- **RAG (Retrieval-Augmented Generation)**: Combines search with generative AI
- **Vector Embeddings**: Semantic similarity search capabilities
- **Conversational AI**: Chat-based interfaces for natural language queries

### 3. **Enterprise-Scale Architecture**

- **Distributed Search**: Apache Solr backend with horizontal scaling
- **Microservices Design**: Spring Boot architecture for modular AI agent development
- **MCP Server**: Model Context Protocol implementation for AI model integration
- **Real-time Processing**: Apache Artemis for asynchronous AI workflows

## AI Agent Capabilities Enabled by Turing

### Knowledge Discovery Agents

Turing enables AI agents that can:

- **Autonomous Content Discovery**: Automatically crawl and index enterprise content
- **Semantic Content Classification**: Use AI to categorize and tag documents intelligently
- **Cross-Source Knowledge Synthesis**: Combine information from multiple enterprise systems
- **Intelligent Content Recommendations**: Suggest relevant content based on user behavior and context

### Conversational Search Agents

- **Natural Language Query Processing**: Transform user questions into semantic search queries
- **Context-Aware Responses**: Maintain conversation history and context
- **Multi-turn Conversations**: Handle complex, multi-step information requests
- **Personalized Results**: Adapt responses based on user roles and permissions

### Enterprise Integration Agents

- **CMS Integration**: Automated content extraction from Adobe AEM, WordPress, and other CMS platforms
- **Database Query Agents**: Intelligent database search across MySQL, PostgreSQL, Oracle, SQL Server
- **File System Intelligence**: Smart document analysis and content extraction
- **API Orchestration**: Coordinate multiple enterprise APIs through intelligent workflows

## Technical Architecture for AI Agents

### Core Components Supporting AI Agents

```mermaid
graph TB
    A[AI Agent Layer] --> B[Turing MCP Server]
    A --> C[Generative AI Module]
    B --> D[Semantic Search Engine]
    C --> E[LangChain4j Framework]
    D --> F[Apache Solr]
    E --> G[Vector Embeddings]
    F --> H[Enterprise Content Sources]
    G --> I[RAG Pipeline]
    H --> J[CMS/DB/Files]
```

### Key Technologies

- **Spring Boot 4**: Microservices foundation for AI agent development
- **Java 21**: Modern language features for AI algorithm implementation
- **LangChain4j**: AI/ML framework integration
- **Apache Solr**: High-performance search engine
- **Apache Artemis**: Message queue for asynchronous AI processing
- **React + TypeScript**: Modern UI for AI agent interfaces
- **Docker & Kubernetes**: Containerized deployment for scalable AI workloads


## SDK and API Support for Agent Development

### Java SDK for AI Agents

```java
// Example: AI Agent using Turing Java SDK
HttpTurSNServer turSNServer = new HttpTurSNServer("http://localhost:2700/api/sn/MySite");

// Semantic search query from AI agent
TurSNQuery query = new TurSNQuery();
query.setQuery("artificial intelligence best practices");
query.setRows(10);
query.setSemanticSearch(true);

// Process results with AI context
QueryTurSNResponse response = turSNServer.query(query);
// AI agent can now process and synthesize results
```

### JavaScript SDK for Web-based Agents

```typescript
// Example: Web-based AI Agent
import { TurSNSiteSearchService } from "@viglet/turing-react-sdk";

class AISearchAgent {
  private searchService: TurSNSiteSearchService;

  constructor(baseURL: string) {
    this.searchService = new TurSNSiteSearchService(baseURL);
  }

  async intelligentSearch(userQuery: string, context?: AgentContext) {
    const results = await this.searchService.search("enterprise-site", {
      q: userQuery,
      rows: 20,
      semanticSearch: true,
      localeRequest: context?.locale || "en_US",
    });

    return this.synthesizeResponse(results, context);
  }
}
```

### REST API for Agent Integration

```bash
# AI Agent making semantic search requests
curl -X POST "http://localhost:2700/api/sn/enterprise-site/search" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "ai-agent-001",
    "query": "machine learning deployment strategies",
    "populateMetrics": true,
    "semanticSearch": true,
    "aiContext": {
      "conversationId": "conv-123",
      "userRole": "data-scientist"
    }
  }'
```

### GraphQL for Complex Agent Queries

```graphql
query AIAgentComplexSearch(
  $siteName: String!
  $query: String!
  $context: AgentContextInput
) {
  siteSearch(
    siteName: $siteName
    searchParams: {
      q: $query
      rows: 50
      semanticSearch: true
      aiEnhanced: true
    }
    context: $context
  ) {
    queryContext {
      count
      responseTime
      semanticScore
    }
    results {
      document {
        fields {
          title
          text
          url
          semanticRelevance
          aiClassification
        }
        aiSummary
        relatedConcepts
      }
    }
    aiInsights {
      topicClusters
      sentimentAnalysis
      knowledgeGaps
    }
  }
}
```

## React Chat UI Architecture

### Chat Page (`turing-react/src/app/console/chat/`)

A Claude AI-inspired conversational interface with:

- **Model selector**: Dropdown to pick enabled LLM instances
- **Streaming responses**: Token-by-token display using SSE
- **Markdown rendering**: `react-markdown` + `remark-gfm` + `rehype-highlight`
- **Code syntax highlighting**: highlight.js with github/github-dark themes, scoped by `.dark` CSS class
- **Theme toggle**: Dark / Light / System mode via `ModeToggle` component
- **File attachments**: Upload button (paperclip icon) + drag & drop + file chips with remove button
- **CSRF handling**: For non-axios `fetch` calls, reads `XSRF-TOKEN` cookie or fetches from `/csrf` endpoint

### Frontend Component Patterns

- **Services**: Class-based (e.g., `TurChatService`): `axios` for REST, native `fetch` for SSE
- **UI components**: shadcn/ui base + custom gradient variants (`GradientButton`, `GradientAvatar`)
- **Icons**: `@tabler/icons-react` as primary icon set
- **State management**: React hooks (`useState`, `useRef`, `useCallback`)
- **Routing**: `react-router-dom` with lazy-loaded route modules in `src/app/routes/`

## Research Applications and Use Cases

### 1. **Enterprise Knowledge Management**

- **Research Focus**: How AI agents can automatically organize and contextualize enterprise knowledge
- **Turing Advantage**: Semantic navigation enables agents to understand document relationships

### 2. **Intelligent Information Retrieval**

- **Research Focus**: Beyond traditional search - understanding intent and context
- **Turing Advantage**: Generative AI integration for query understanding and response synthesis

### 3. **Multi-Source Data Fusion**

- **Research Focus**: How AI agents can synthesize information from disparate enterprise systems
- **Turing Advantage**: Native connectors to CMS, databases, file systems

### 4. **Conversational Enterprise Search**

- **Research Focus**: Natural language interfaces for complex enterprise queries
- **Turing Advantage**: Built-in chatbot framework with context awareness

## Deployment and Scalability for AI Workloads

### Container-Based Deployment

```yaml
# Docker Compose for AI Agent Development
version: "3.8"
services:
  turing-ai-platform:
    image: openviglet/turing:latest
    environment:
      - AI_ENABLED=true
      - LANGCHAIN_API_KEY=${LANGCHAIN_API_KEY}
      - VECTOR_STORE=chroma
    ports:
      - "2700:2700"
    depends_on:
      - turing-solr
      - turing-db
      - ai-vector-store
```

### Kubernetes for Production AI Agents

- **Horizontal Pod Autoscaling**: Scale AI workloads based on query load
- **GPU Support**: Integration with CUDA for AI model processing
- **Service Mesh**: Istio integration for AI agent communication

## Performance Characteristics for AI Applications

### Search Performance

- **Sub-second Response Times**: Critical for real-time AI agent interactions
- **Concurrent Query Support**: Handle multiple AI agents simultaneously

## Integration with AI/ML Frameworks

### Supported AI Frameworks

- **LangChain4j**: Primary integration for AI agent development
- **Spring AI**: Enterprise AI application development

### Model Integration

- **Local Model Support**: Run AI models within the Turing infrastructure
- **Cloud API Integration**: Connect to OpenAI, Claude, Gemini APIs

## Research Data and Benchmarks

## Security and Privacy for AI Agents

### Enterprise Security Features

- **Authentication**: Keycloak integration for AI agent identity management
- **Authorization**: Role-based access control for AI agent permissions
- **Audit Logging**: Track AI agent actions and data access

### AI-Specific Security

- **Model Security**: Prevent AI model poisoning and adversarial attacks
- **Data Isolation**: Ensure AI agents only access authorized content

## Community and Collaboration

### Research Collaboration Opportunities

- **Open Source**: Apache 2.0 license enables research collaboration

### Developer Community

- **GitHub**: https://github.com/openviglet/turing
- **Discussions**: https://github.com/openviglet/turing/discussions
- **Documentation**: https://docs.viglet.org/turing/

## Future Roadmap for AI Agent Capabilities

### Planned AI Features

- **Multi-modal Search**: Support for image, video, and audio content analysis
- **Federated Learning**: Distributed AI model training across enterprise sites

### Research Integration Roadmap

- **Academic Research APIs**: Specialized endpoints for research applications
- **Benchmark Suite**: Standardized evaluation tools for enterprise search AI

## Getting Started with AI Agent Development

### Quick Start for Researchers

```bash
# Clone and setup for AI research
git clone https://github.com/openviglet/turing.git
cd turing

# Enable AI features
export AI_ENABLED=true
export LANGCHAIN_API_KEY=your_api_key

# Start with AI capabilities
docker-compose -f docker-compose.ai.yml up -d

# Access research APIs at http://localhost:2700/api/ai/
```

### Development Environment Setup

1. **Prerequisites**: Java 21+, Docker, AI model access

## Conclusion

Viglet Turing provides a comprehensive foundation for AI agent research in enterprise search environments. Its combination of semantic search capabilities, generative AI integration, and enterprise-grade architecture makes it an ideal platform for advancing the state of the art in intelligent information retrieval and knowledge management systems.

The platform's open-source nature, comprehensive APIs, and scalable architecture provide researchers with the tools needed to develop, test, and deploy sophisticated AI agents that can transform how organizations interact with their knowledge assets.

---

\*For research collaborations, technical questions, or contribution opportunities, please contact:

**License**: Apache 2.0 - enabling open research and collaboration  
**Repository**: https://github.com/openviglet/turing  
**Documentation**: https://docs.viglet.org/turing/
