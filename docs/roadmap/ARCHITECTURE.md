# Viglet Turing - Architecture Documentation

## Table of Contents
1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Component Architecture](#component-architecture)
4. [Design Principles](#design-principles)
5. [Technology Stack](#technology-stack)
6. [Data Flow](#data-flow)
7. [Integration Patterns](#integration-patterns)
8. [Security Architecture](#security-architecture)

## Overview

Viglet Turing is an **Enterprise Search Platform** built on modern Java and Spring Boot architecture. The system provides semantic search capabilities, AI-powered features, and extensive integration options for enterprise content sources.

### Core Capabilities
- **Semantic Search**: Advanced content understanding and contextual search
- **AI Integration**: Generative AI and LangChain4j support for RAG (Retrieval-Augmented Generation)
- **Multi-Source Indexing**: Connectors for CMS, databases, filesystems, and web crawling
- **Real-time Processing**: Asynchronous message processing with Apache Artemis
- **Scalable Architecture**: Horizontal scaling with Apache Solr and Elasticsearch

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Turing Enterprise Search                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  React UI    │  │  Java SDK    │  │  JavaScript SDK      │ │
│  │  (turing-ui) │  │              │  │                      │ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘ │
│         │                 │                      │             │
│         └─────────────────┴──────────────────────┘             │
│                           │                                     │
├───────────────────────────┼─────────────────────────────────────┤
│                    REST/GraphQL API                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │           Turing App (Spring Boot)                     │   │
│  │                                                         │   │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────────┐   │   │
│  │  │   API    │  │ Business │  │   Integration      │   │   │
│  │  │  Layer   │  │  Logic   │  │    Services        │   │   │
│  │  └────┬─────┘  └────┬─────┘  └─────────┬──────────┘   │   │
│  │       │             │                   │              │   │
│  │  ┌────┴─────────────┴───────────────────┴──────────┐   │   │
│  │  │           Persistence Layer (JPA/Hibernate)     │   │   │
│  │  └──────────────────────┬──────────────────────────┘   │   │
│  └─────────────────────────┼──────────────────────────────┘   │
│                            │                                   │
├────────────────────────────┼───────────────────────────────────┤
│                    Message Queue Layer                         │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │         Apache Artemis (Async Processing)               │  │
│  └─────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  Database    │  │ Search Engine│  │   AI/GenAI           │ │
│  │ (H2/MariaDB) │  │ (Solr/ES)    │  │   (LangChain4j)      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### 1. Turing App (Core Application)

**Module**: `turing-app`

The main Spring Boot application serving as the platform's core. It follows a layered architecture:

#### API Layer (`com.viglet.turing.api`)
- **Purpose**: REST and GraphQL endpoints for external communication
- **Responsibilities**:
  - Request handling and validation
  - Response formatting
  - API versioning
  - Swagger/OpenAPI documentation
- **Key Components**:
  - `TurSEInstanceAPI`: Search engine instance management
  - `TurSNSiteSearchAPI`: Semantic navigation search APIs
  - `TurIntegrationAPI`: Integration instance management
  - GraphQL controllers for flexible querying

#### Business Logic Layer
- **Purpose**: Core business rules and processing
- **Responsibilities**:
  - Search query processing
  - Content indexing logic
  - Security and access control
  - Workflow orchestration
- **Key Packages**:
  - `com.viglet.turing.sn`: Semantic navigation logic
  - `com.viglet.turing.se`: Search engine abstraction
  - `com.viglet.turing.genai`: Generative AI integration
  - `com.viglet.turing.exchange`: Import/export functionality

#### Persistence Layer (`com.viglet.turing.persistence`)
- **Purpose**: Data access and management
- **Responsibilities**:
  - JPA entity definitions
  - Repository interfaces
  - Database transactions
  - Query optimization
- **Structure**:
  - `model/`: JPA entities (e.g., `TurSEInstance`, `TurSNSite`)
  - `repository/`: Spring Data JPA repositories
  - `bean/`: Data transfer objects

#### Integration Layer (`com.viglet.turing.plugins`)
- **Purpose**: External system connectors
- **Responsibilities**:
  - CMS connector implementations
  - Database connectors
  - Web crawler integration
  - Plugin lifecycle management

### 2. Turing Commons

**Module**: `turing-commons`

Shared utilities and common functionality:
- Data transfer objects (DTOs)
- Common constants and enums
- Shared utility classes
- Cross-cutting concerns

### 3. Turing Spring

**Module**: `turing-spring`

Spring-specific utilities and configurations:
- Spring Boot configurations
- Bean definitions
- Utility classes for Spring framework

### 4. Turing Java SDK

**Module**: `turing-java-sdk`

Client library for Java applications:
- Search client implementations
- Indexing API clients
- Authentication helpers
- Type-safe API wrappers

### 5. Turing React SDK

**Module**: `turing-react-sdk`

React SDK with hooks and UI components:
- NPM package `@viglet/turing-react-sdk`
- TypeScript type definitions
- Headless hooks for search, facets, pagination
- Pre-built UI components (SearchBar, ResultList, Pagination)

### 6. Turing UI

**Module**: `turing-ui`

Modern React-based user interface:
- Administrative console
- Search interface components
- Configuration management UI
- Analytics dashboards

### 7. Turing MCP Server

**Module**: `turing-mcp-server`

Model Context Protocol server for AI integration:
- AI model context management
- LangChain4j integration
- Vector embedding support
- RAG pipeline implementation

## Design Principles

### 1. Separation of Concerns
- Clear boundaries between layers (API, Business Logic, Persistence)
- Single Responsibility Principle applied to classes
- Package organization reflects functional boundaries

### 2. Dependency Injection
- Spring Framework's IoC container
- Constructor-based injection (preferred)
- Interface-based programming for flexibility

### 3. API-First Design
- RESTful API principles
- OpenAPI/Swagger documentation
- GraphQL for flexible queries
- Versioning strategy for backward compatibility

### 4. Scalability Considerations
- Stateless application design
- Horizontal scaling capability
- Asynchronous processing for long-running tasks
- Caching strategies (Spring Cache)

### 5. Security by Design
- Authentication and authorization
- Input validation and sanitization
- SQL injection prevention (JPA/Hibernate)
- XSS protection
- HTTPS/TLS support

## Technology Stack

### Backend
- **Language**: Java 21+
- **Framework**: Spring Boot 4.0.1
- **ORM**: Hibernate 7 with JPA
- **Database**: H2 (dev), MariaDB/MySQL (prod)
- **Search**: Apache Solr 9.x, Elasticsearch support
- **Message Queue**: Apache Artemis
- **Build Tool**: Maven 3.6+
- **API Documentation**: Swagger/OpenAPI 3

### Frontend
- **Framework**: React
- **Language**: TypeScript/JavaScript
- **Build Tool**: npm/webpack
- **UI Components**: Angular Octicons

### AI/ML Integration
- **Framework**: LangChain4j
- **Vector DB**: Support for embeddings
- **GenAI**: OpenAI, Azure OpenAI integration

### DevOps
- **Containerization**: Docker
- **Orchestration**: Kubernetes (Helm charts)
- **CI/CD**: GitHub Actions
- **Code Quality**: SonarCloud

## Data Flow

### 1. Content Indexing Flow

```
Content Source → Connector → Artemis Queue → Index Processor → 
Search Engine (Solr/ES) → Index Confirmation → Database
```

**Steps**:
1. Content source triggers indexing (manual or scheduled)
2. Connector extracts content and metadata
3. Message sent to Artemis queue for async processing
4. Index processor transforms and normalizes data
5. Content indexed in search engine
6. Metadata stored in relational database

### 2. Search Query Flow

```
Client → REST/GraphQL API → Query Processor → Search Engine → 
Result Aggregator → Response Formatter → Client
```

**Steps**:
1. Client submits search query
2. API layer validates and routes request
3. Query processor applies business logic (filters, facets, etc.)
4. Search engine executes query
5. Results aggregated and enhanced
6. Response formatted and returned to client

### 3. AI-Powered Search Flow (RAG)

```
Query → Embedding Generator → Vector Search → Context Retrieval → 
LLM Processor → Response Generation → Client
```

**Steps**:
1. User query converted to vector embedding
2. Semantic similarity search performed
3. Relevant documents retrieved as context
4. Context + query sent to LLM
5. Generated response returned to user

## Integration Patterns

### 1. Connector Pattern
- Plugin-based architecture for content sources
- Common interface: `TurIntegrationVendor`
- Factory pattern for connector instantiation
- Configuration-driven connector selection

### 2. Repository Pattern
- Spring Data JPA repositories
- Custom query methods
- Specification pattern for complex queries
- Transaction management at service layer

### 3. Facade Pattern
- Search engine abstraction (`TurSolr`, `TurElasticsearch`)
- Unified interface for multiple search engine backends
- Vendor-specific implementations hidden from business logic

### 4. Observer Pattern
- Spring Events for decoupled communication
- Asynchronous message processing with Artemis
- Event-driven indexing workflows

### 5. Strategy Pattern
- Multiple search strategies (relevance, date, popularity)
- Pluggable ranking algorithms
- Configurable facet strategies

## Security Architecture

### Authentication
- Spring Security integration
- Session-based authentication
- API key support for programmatic access
- Future: OAuth2/OIDC support

### Authorization
- Role-based access control (RBAC)
- Resource-level permissions
- API endpoint protection
- Admin vs. user roles

### Data Protection
- Input validation on all endpoints
- SQL injection prevention via JPA
- XSS protection on rendered content
- CSRF protection on state-changing operations
- Sensitive data encryption at rest

### Network Security
- HTTPS/TLS enforcement in production
- CORS configuration for API access
- Rate limiting on public APIs
- Firewall rules for backend services

## Performance Considerations

### Caching Strategy
- Spring Cache abstraction
- Multiple cache levels:
  - Application cache (in-memory)
  - Search result cache
  - Metadata cache
- Cache invalidation strategies

### Database Optimization
- Indexed columns for frequent queries
- Connection pooling (HikariCP)
- Lazy loading for associations
- Batch operations for bulk updates

### Search Engine Optimization
- Query result caching
- Filter cache warmup
- Shard distribution
- Replica configuration

### Asynchronous Processing
- Message queue for long-running operations
- Background job processing
- Scheduled tasks for maintenance
- Non-blocking I/O where applicable

## Monitoring and Observability

### Logging
- SLF4J with Logback
- Structured logging format
- Log levels per package
- Audit logging for security events

### Metrics
- Spring Actuator endpoints
- JVM metrics
- Custom application metrics
- Search performance metrics

### Health Checks
- Database connectivity
- Search engine availability
- Message queue status
- External service dependencies

## Deployment Architecture

### Development Environment
- Embedded H2 database
- Embedded Solr instance
- Hot reload support
- Debug configuration

### Production Environment
- External MariaDB cluster
- Apache Solr cluster with replication
- Load-balanced application instances
- Redis for distributed caching

### Container Deployment
- Docker Compose for local development
- Kubernetes for production (Helm charts available)
- Horizontal pod autoscaling
- Persistent volume claims for data

## Future Architecture Considerations

### Microservices Evolution
- Potential service decomposition:
  - Search Service
  - Indexing Service
  - Analytics Service
  - AI/GenAI Service
- Service mesh for inter-service communication
- Event-driven architecture

### Cloud-Native Features
- Cloud storage integration (S3, Azure Blob)
- Managed database services
- Serverless functions for lightweight tasks
- Container orchestration (EKS, AKS, GKE)

### Advanced AI Integration
- Vector database integration (Pinecone, Weaviate)
- Advanced RAG patterns
- Multi-modal search (text, image, video)
- Real-time personalization

---

**Document Version**: 1.0  
**Last Updated**: 2026-01-04  
**Maintainer**: Viglet Team
