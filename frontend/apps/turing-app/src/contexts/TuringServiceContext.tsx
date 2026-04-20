import { TurIntegrationInstanceService } from '@/services/integration/integration-instance.service';
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

interface TuringServiceContextType {
    axiosInstance: AxiosInstance;

    // Integration Services
    integrationInstanceService: TurIntegrationInstanceService;

    // LLM Services
    llmService: TurLLMInstanceService;

    // Search Engine (SE) Services
    seService: TurSEInstanceService;

    // Semantic Navigation (SN) Services
    snService: TurSNSiteService;
    snFieldService: TurSNFieldService;
    snFieldTypeService: TurSNFieldTypeService;
    snFacetedFieldService: TurSNFacetedFieldService;
    snSiteLocaleService: TurSNSiteLocaleService;
    snSiteMergeService: TurSNSiteMergeService;
    snSiteResultRankingService: TurSNRankingExpressionService;
    snSiteSpotlightService: TurSNSiteSpotlightService;

    // Store Services
    storeService: TurStoreInstanceService;
}

const TuringServiceContext = createContext<TuringServiceContextType | null>(null);

interface TuringServiceProviderProps {
    readonly children: ReactNode;
    readonly axiosInstance?: AxiosInstance;
}

export function TuringServiceProvider({
    children,
    axiosInstance = axios
}: TuringServiceProviderProps) {
    const services = useMemo<TuringServiceContextType>(() => ({
        axiosInstance,
        integrationInstanceService: new TurIntegrationInstanceService(axiosInstance),
        llmService: new TurLLMInstanceService(),
        seService: new TurSEInstanceService(),
        snService: new TurSNSiteService(),
        snFieldService: new TurSNFieldService(),
        snFieldTypeService: new TurSNFieldTypeService(),
        snFacetedFieldService: new TurSNFacetedFieldService(),
        snSiteLocaleService: new TurSNSiteLocaleService(),
        snSiteMergeService: new TurSNSiteMergeService(),
        snSiteResultRankingService: new TurSNRankingExpressionService(),
        snSiteSpotlightService: new TurSNSiteSpotlightService(),
        storeService: new TurStoreInstanceService(),
    }), [axiosInstance]);

    return (
        <TuringServiceContext.Provider value={services}>
            {children}
        </TuringServiceContext.Provider>
    );
}

export function useTuringService(): TuringServiceContextType {
    const context = useContext(TuringServiceContext);
    if (!context) {
        throw new Error('useTuringService must be used within TuringServiceProvider.');
    }
    return context;
}

export function useLLMService() {
    const { llmService } = useTuringService();
    return llmService;
}

export function useSEService() {
    const { seService } = useTuringService();
    return seService;
}

export function useSNService() {
    const { snService } = useTuringService();
    return snService;
}

export function useSNFieldService() {
    const { snFieldService } = useTuringService();
    return snFieldService;
}

export function useStoreService() {
    const { storeService } = useTuringService();
    return storeService;
}

export function useIntegrationInstanceService() {
    const { integrationInstanceService } = useTuringService();
    return integrationInstanceService;
}
