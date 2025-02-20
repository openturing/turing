import { TurNLPVendor } from "../../nlp/model/nlp-vendor.model";
import { TurSEInstance } from "../../se/model/se-instance.model";
import { TurSNSiteLocale } from "./sn-site-locale.model";
import { TurSNSiteFacetEnum} from "./sn-site-facet.enum";
import {TurSNSiteFacetSortEnum} from "./sn-site-facet-sort.enum";
import {TurLLMInstance} from "../../llm/model/llm-instance.model";
import {TurStoreInstance} from "../../store/model/store-instance.model";

export interface TurSNSiteGenAi {
  id: string;
  turLLMInstance: TurLLMInstance;
  turStoreInstance: TurStoreInstance;
  enabled: boolean;
  systemPrompt: string;
}
