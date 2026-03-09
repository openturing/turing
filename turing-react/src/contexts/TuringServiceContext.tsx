import { TurIntegrationAemSourceService } from '@/services/integration/integration-aem-source.service';
import { TurIntegrationConnectorService } from '@/services/integration/integration-connector.service';
import { TurIntegrationIndexingRuleService } from '@/services/integration/integration-indexing-rule.service';
import { TurIntegrationInstanceService } from '@/services/integration/integration-instance.service';
import { TurIntegrationMonitoringService } from '@/services/integration/integration-monitoring.service';
import { TurIntegrationVendorService } from '@/services/integration/integration-vendor.service';
import { TurIntegrationWcSourceService } from '@/services/integration/integration-wc-source.service';
import { TurLLMInstanceService } from '@/services/llm/llm.service';
import { TurSEInstanceService } from '@/services/se/se.service';
import { TurSNFacetedFieldService } from '@/services/sn/sn.faceted.field.service';
import { TurSNFieldService } from '@/services/sn/sn.field.service';
import { TurSNFieldTypeService } from '@/services/sn/sn.field.type.service';
import { TurSNSiteService } from '@/services/sn/sn.service';
import { TurSNSiteLocaleService } from '@/services/sn/sn.site.locale.service';
import { TurSNSiteMergeService } from '@/services/sn/sn.site.merge.service';
import { TurSNRankingExpressionService } from '@/services/sn/sn.site.result.ranking.service';
import { TurSNSiteSpotlightService } from '@/services/sn/sn.site.spotlight.service';
import { TurStoreInstanceService } from '@/services/store/store.service';
import axios, { type AxiosInstance } from 'axios';
import { createContext, useContext, useMemo, type ReactNode } from 'react';

/**
 * Interface centralizada para todos os serviços do Turing
 * Suporta AI Agents através do MCP Server
 * Alinhado com a arquitetura Enterprise Search Intelligence Platform
 */
interface TuringServiceContextType {
    // ========== Axios Instance ==========
    axiosInstance: AxiosInstance;

    // ========== Integration Services ==========
    integrationInstanceService: TurIntegrationInstanceService;
    integrationVendorService: TurIntegrationVendorService;

    // Factory methods para serviços que precisam de parâmetros
    createAemSourceService: (integrationId: string) => TurIntegrationAemSourceService;
    createWcSourceService: (integrationId: string) => TurIntegrationWcSourceService;
    createConnectorService: (integrationId: string) => TurIntegrationConnectorService;
    createIndexingRuleService: (integrationId: string) => TurIntegrationIndexingRuleService;
    createMonitoringService: (integrationId: string) => TurIntegrationMonitoringService;

    // ========== LLM Services ==========
    llmService: TurLLMInstanceService;

    // ========== Search Engine (SE) Services ==========
    seService: TurSEInstanceService;

    // ========== Semantic Navigation (SN) Services ==========
    snService: TurSNSiteService;
    snFieldService: TurSNFieldService;
    snFieldTypeService: TurSNFieldTypeService;
    snFacetedFieldService: TurSNFacetedFieldService;
    snSiteLocaleService: TurSNSiteLocaleService;
    snSiteMergeService: TurSNSiteMergeService;
    snSiteResultRankingService: TurSNRankingExpressionService;
    snSiteSpotlightService: TurSNSiteSpotlightService;

    // ========== Store Services ==========
    storeService: TurStoreInstanceService;
}

const TuringServiceContext = createContext<TuringServiceContextType | null>(null);

interface TuringServiceProviderProps {
    readonly children: ReactNode;
    readonly axiosInstance?: AxiosInstance;
}

/**
 * Provider centralizado para TODOS os serviços do Turing
 * Suporta AI Agents através do MCP Server
 * Alinhado com a arquitetura Enterprise Search Intelligence Platform
 * 
 * @example
 * ```tsx
 * <TuringServiceProvider>
 *   <App />
 * </TuringServiceProvider>
 * ```
 */
export function TuringServiceProvider({
    children,
    axiosInstance = axios
}: TuringServiceProviderProps) {
    // Memoize todos os serviços para evitar re-instanciação
    const services = useMemo<TuringServiceContextType>(() => ({
        // Axios Instance
        axiosInstance,

        // ========== Integration Services ==========
        integrationInstanceService: new TurIntegrationInstanceService(axiosInstance),
        integrationVendorService: new TurIntegrationVendorService(axiosInstance),

        // Factory methods para serviços parametrizados
        createAemSourceService: (integrationId: string) =>
            new TurIntegrationAemSourceService(integrationId, axiosInstance),
        createWcSourceService: (integrationId: string) =>
            new TurIntegrationWcSourceService(integrationId, axiosInstance),
        createConnectorService: (integrationId: string) =>
            new TurIntegrationConnectorService(integrationId, axiosInstance),
        createIndexingRuleService: (integrationId: string) =>
            new TurIntegrationIndexingRuleService(integrationId, axiosInstance),
        createMonitoringService: (integrationId: string) =>
            new TurIntegrationMonitoringService(integrationId, axiosInstance),

        // ========== LLM Services ==========
        llmService: new TurLLMInstanceService(),

        // ========== Search Engine (SE) Services ==========
        seService: new TurSEInstanceService(),

        // ========== Semantic Navigation (SN) Services ==========
        snService: new TurSNSiteService(),
        snFieldService: new TurSNFieldService(),
        snFieldTypeService: new TurSNFieldTypeService(),
        snFacetedFieldService: new TurSNFacetedFieldService(),
        snSiteLocaleService: new TurSNSiteLocaleService(),
        snSiteMergeService: new TurSNSiteMergeService(),
        snSiteResultRankingService: new TurSNRankingExpressionService(),
        snSiteSpotlightService: new TurSNSiteSpotlightService(),

        // ========== Store Services ==========
        storeService: new TurStoreInstanceService(),
    }), [axiosInstance]);

    return (
        <TuringServiceContext.Provider value={services}>
            {children}
        </TuringServiceContext.Provider>
    );
}

/**
 * Hook principal para acessar TODOS os serviços do Turing
 * Usado por componentes e AI Agents
 * 
 * @throws Error se usado fora do TuringServiceProvider
 * 
 * @example
 * ```tsx
 * const { llmService, snService, integrationInstanceService } = useTuringService();
 * const llmInstances = await llmService.query();
 * ```
 */
export function useTuringService(): TuringServiceContextType {
    const context = useContext(TuringServiceContext);
    if (!context) {
        throw new Error(
            'useTuringService must be used within TuringServiceProvider. ' +
            'This is required for AI Agent and MCP Server integration.'
        );
    }
    return context;
}

// ========== Hooks Específicos para Serviços Comuns ==========

/**
 * Hook para serviço de LLM
 */
export function useLLMService() {
    const { llmService } = useTuringService();
    return llmService;
}

/**
 * Hook para serviço de Search Engine
 */
export function useSEService() {
    const { seService } = useTuringService();
    return seService;
}

/**
 * Hook para serviço de Semantic Navigation (Site)
 */
export function useSNService() {
    const { snService } = useTuringService();
    return snService;
}

/**
 * Hook para serviço de SN Fields
 */
export function useSNFieldService() {
    const { snFieldService } = useTuringService();
    return snFieldService;
}

/**
 * Hook para serviço de Store
 */
export function useStoreService() {
    const { storeService } = useTuringService();
    return storeService;
}

/**
 * Hook para serviço de Integration Instance
 */
export function useIntegrationInstanceService() {
    const { integrationInstanceService } = useTuringService();
    return integrationInstanceService;
}

/**
 * Hook para criar AEM Source Service com um integrationId específico
 * @param integrationId - ID da integração
 */
export function useAemSourceService(integrationId: string) {
    const { createAemSourceService } = useTuringService();
    return useMemo(
        () => createAemSourceService(integrationId),
        [createAemSourceService, integrationId]
    );
}

/**
 * Hook para criar Web Crawler Source Service com um integrationId específico
 * @param integrationId - ID da integração
 */
export function useWcSourceService(integrationId: string) {
    const { createWcSourceService } = useTuringService();
    return useMemo(
        () => createWcSourceService(integrationId),
        [createWcSourceService, integrationId]
    );
}

/**
 * Hook para criar Connector Service com um integrationId específico
 * @param integrationId - ID da integração
 */
export function useConnectorService(integrationId: string) {
    const { createConnectorService } = useTuringService();
    return useMemo(
        () => createConnectorService(integrationId),
        [createConnectorService, integrationId]
    );
}

/**
 * Hook para criar Indexing Rule Service com um integrationId específico
 * @param integrationId - ID da integração
 */
export function useIndexingRuleService(integrationId: string) {
    const { createIndexingRuleService } = useTuringService();
    return useMemo(
        () => createIndexingRuleService(integrationId),
        [createIndexingRuleService, integrationId]
    );
}

/**
 * Hook para criar Monitoring Service com um integrationId específico
 * @param integrationId - ID da integração
 */
export function useMonitoringService(integrationId: string) {
    const { createMonitoringService } = useTuringService();
    return useMemo(
        () => createMonitoringService(integrationId),
        [createMonitoringService, integrationId]
    );
}
