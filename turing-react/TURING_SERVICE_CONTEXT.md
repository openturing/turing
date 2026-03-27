# Turing Service Context - Guia de Uso

## 📋 Visão Geral

O `TuringServiceContext` é uma solução consolidada que centraliza **TODOS** os serviços do Turing em um único Context API, facilitando o uso em toda a aplicação e alinhado com a arquitetura de AI Agents e MCP Server.

## 🎯 Benefícios

✅ **Centralizado**: Um único ponto de acesso para todos os serviços  
✅ **Performance**: Instâncias memoizadas e otimizadas  
✅ **Type-Safe**: TypeScript completo com IntelliSense  
✅ **Testável**: Fácil mockar em testes unitários  
✅ **Escalável**: Preparado para novos serviços  
✅ **AI Agents Ready**: Arquitetura alinhada com MCP Server  

## 🚀 Instalação e Configuração

### 1. Provider já está configurado no App.tsx

```tsx
import { TuringServiceProvider } from './contexts/TuringServiceContext';

function App() {
  return (
    <TuringServiceProvider>
      {/* Seus componentes aqui */}
    </TuringServiceProvider>
  );
}
```

## 📖 Exemplos de Uso

### Exemplo 1: LLM Service (Serviço Simples)

```tsx
import { useLLMService } from '@/contexts/TuringServiceContext';

function LLMList() {
  const llmService = useLLMService();
  const [instances, setInstances] = useState([]);

  useEffect(() => {
    const loadData = async () => {
      const data = await llmService.query();
      setInstances(data);
    };
    loadData();
  }, [llmService]);

  return (
    <ul>
      {instances.map(instance => (
        <li key={instance.id}>{instance.name}</li>
      ))}
    </ul>
  );
}
```

### Exemplo 2: Semantic Navigation Service

```tsx
import { useSNService, useSNFieldService } from '@/contexts/TuringServiceContext';

function SNSiteManager() {
  const snService = useSNService();
  const snFieldService = useSNFieldService();

  const createSite = async (siteData) => {
    const site = await snService.create(siteData);
    console.log('Site created:', site);
  };

  const loadFields = async (siteId: string) => {
    const fields = await snFieldService.query(siteId);
    console.log('Fields:', fields);
  };

  return <div>/* ... */</div>;
}
```

### Exemplo 3: Integration Service (com integrationId)

```tsx
import { 
  useAemSourceService, 
  useIntegrationInstanceService 
} from '@/contexts/TuringServiceContext';

function IntegrationManager({ integrationId }: { integrationId: string }) {
  // Serviço que precisa de integrationId (memoizado automaticamente)
  const aemSourceService = useAemSourceService(integrationId);
  
  // Serviço simples
  const integrationInstanceService = useIntegrationInstanceService();

  const loadSources = async () => {
    const sources = await aemSourceService.query();
    console.log('AEM Sources:', sources);
  };

  const loadInstances = async () => {
    const instances = await integrationInstanceService.query();
    console.log('Integration Instances:', instances);
  };

  return <div>/* ... */</div>;
}
```

### Exemplo 4: Múltiplos Serviços no Mesmo Componente

```tsx
import { useTuringService } from '@/contexts/TuringServiceContext';

function Dashboard() {
  // Acessa múltiplos serviços de uma vez
  const {
    llmService,
    snService,
    seService,
    storeService,
    integrationInstanceService
  } = useTuringService();

  useEffect(() => {
    const loadDashboard = async () => {
      const [llms, sites, engines, stores, integrations] = await Promise.all([
        llmService.query(),
        snService.query(),
        seService.query(),
        storeService.query(),
        integrationInstanceService.query()
      ]);

      console.log('Dashboard data loaded:', {
        llms,
        sites,
        engines,
        stores,
        integrations
      });
    };

    loadDashboard();
  }, [llmService, snService, seService, storeService, integrationInstanceService]);

  return <div>/* Dashboard UI */</div>;
}
```

### Exemplo 5: Criando Serviços Dinamicamente

```tsx
import { useTuringService } from '@/contexts/TuringServiceContext';

function DynamicIntegrationForm() {
  const { createAemSourceService, createWcSourceService } = useTuringService();
  const [integrationId, setIntegrationId] = useState('');
  const [vendorType, setVendorType] = useState<'AEM' | 'WEB_CRAWLER'>('AEM');

  const handleSubmit = async (data: any) => {
    // Cria o serviço apropriado baseado no vendor
    const service = vendorType === 'AEM' 
      ? createAemSourceService(integrationId)
      : createWcSourceService(integrationId);

    const result = await service.create(data);
    console.log('Created:', result);
  };

  return <form onSubmit={handleSubmit}>/* ... */</form>;
}
```

### Exemplo 6: Com React Hook Form

```tsx
import { useAemSourceService } from '@/contexts/TuringServiceContext';
import { useForm } from 'react-hook-form';

function AemSourceForm({ integrationId }: { integrationId: string }) {
  const aemSourceService = useAemSourceService(integrationId);
  const { handleSubmit, register } = useForm();

  const onSubmit = async (data: any) => {
    try {
      const result = await aemSourceService.create(data);
      toast.success('AEM Source created successfully!');
    } catch (error) {
      toast.error('Failed to create AEM Source');
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register('title')} placeholder="Title" />
      <input {...register('endpoint')} placeholder="Endpoint" />
      <button type="submit">Save</button>
    </form>
  );
}
```

### Exemplo 7: Com Async/Await e Loading States

```tsx
import { useSEService } from '@/contexts/TuringServiceContext';
import { useState } from 'react';

function SearchEngineList() {
  const seService = useSEService();
  const [engines, setEngines] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadEngines = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await seService.query();
      setEngines(data);
    } catch (err) {
      setError('Failed to load search engines');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <button onClick={loadEngines} disabled={loading}>
        {loading ? 'Loading...' : 'Load Engines'}
      </button>
      {error && <div className="error">{error}</div>}
      <ul>
        {engines.map(engine => <li key={engine.id}>{engine.name}</li>)}
      </ul>
    </div>
  );
}
```

## 🧪 Testes Unitários

### Mockando o Context em Testes

```tsx
import { render, screen } from '@testing-library/react';
import { TuringServiceContext } from '@/contexts/TuringServiceContext';
import { MyComponent } from './MyComponent';

describe('MyComponent', () => {
  it('should load LLM instances', async () => {
    // Mock do LLM Service
    const mockLLMService = {
      query: jest.fn().mockResolvedValue([
        { id: '1', name: 'GPT-4' },
        { id: '2', name: 'Claude' }
      ])
    };

    // Mock do contexto
    const mockContext = {
      llmService: mockLLMService,
      // ... outros serviços mockados
    };

    render(
      <TuringServiceContext.Provider value={mockContext}>
        <MyComponent />
      </TuringServiceContext.Provider>
    );

    // Suas assertions aqui
    expect(mockLLMService.query).toHaveBeenCalled();
  });
});
```

## 📚 Lista Completa de Hooks Disponíveis

### Hooks Simples (Serviços sem parâmetros)
- `useLLMService()` - LLM Instance Service
- `useSEService()` - Search Engine Service  
- `useSNService()` - Semantic Navigation Site Service
- `useSNFieldService()` - SN Field Service
- `useStoreService()` - Store Instance Service
- `useIntegrationInstanceService()` - Integration Instance Service

### Hooks Parametrizados (Precisam de ID)
- `useAemSourceService(integrationId)` - AEM Source Service
- `useWcSourceService(integrationId)` - Web Crawler Source Service
- `useConnectorService(integrationId)` - Connector Service
- `useIndexingRuleService(integrationId)` - Indexing Rule Service

### Hook Universal (Acesso a TODOS os serviços)
- `useTuringService()` - Retorna todos os serviços e factories

## 🔧 Serviços Disponíveis

### Integration Services
- `integrationInstanceService` - Gerenciamento de instâncias de integração
- `integrationVendorService` - Vendors de integração (AEM, Web Crawler)
- `integrationMonitoringService` - Monitoramento de integrações
- `createAemSourceService(id)` - Factory para AEM Source
- `createWcSourceService(id)` - Factory para Web Crawler Source
- `createConnectorService(id)` - Factory para Connectors
- `createIndexingRuleService(id)` - Factory para Indexing Rules

### LLM Services
- `llmService` - Gerenciamento de instâncias LLM

### Search Engine Services
- `seService` - Gerenciamento de Search Engines

### Semantic Navigation Services
- `snService` - Sites SN
- `snFieldService` - Fields de sites SN
- `snFieldTypeService` - Tipos de fields
- `snFacetedFieldService` - Faceted fields
- `snSiteLocaleService` - Locales de sites
- `snSiteMergeService` - Merge de sites
- `snSiteResultRankingService` - Ranking de resultados
- `snSiteSpotlightService` - Spotlight de sites

### Store Services
- `storeService` - Gerenciamento de stores

## 🎨 Padrões e Best Practices

### ✅ Boas Práticas

```tsx
// ✅ BOM: Usar hooks específicos quando possível
const llmService = useLLMService();

// ✅ BOM: Memoização automática com hooks parametrizados
const aemService = useAemSourceService(integrationId);

// ✅ BOM: Usar useTuringService quando precisar de múltiplos serviços
const { llmService, snService, seService } = useTuringService();
```

### ❌ Anti-Padrões

```tsx
// ❌ RUIM: Criar instâncias manualmente
const service = new TurLLMInstanceService(); // NÃO FAÇA ISSO

// ❌ RUIM: Não memoizar serviços parametrizados
function MyComponent({ integrationId }) {
  // Re-cria serviço a cada render!
  const service = createAemSourceService(integrationId);
}

// ✅ BOM: Usar o hook que já memoiza
function MyComponent({ integrationId }) {
  const service = useAemSourceService(integrationId);
}
```

## 🚀 Próximos Passos

Para adicionar um novo serviço ao contexto:

1. Importe o serviço em `TuringServiceContext.tsx`
2. Adicione à interface `TuringServiceContextType`
3. Instancie no `useMemo` do provider
4. (Opcional) Crie um hook específico para facilitar o uso

## 📞 Suporte

Para dúvidas ou problemas, consulte:
- Documentação: https://docs.viglet.org/turing/
- GitHub: https://github.com/openviglet/turing
- Issues: https://github.com/openviglet/turing/issues

---

**Arquitetura Enterprise Search Intelligence Platform**  
Alinhado com AI Agents e MCP Server  
Apache 2.0 License
