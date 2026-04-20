import type {
  TurSNSiteSearchRule,
  TurSNSiteSearchRuleFieldOption,
} from "@/models/sn/sn-site-search-rule.model";
import axios from "axios";

export class TurSNSiteSearchRuleService {
  async query(snSiteId: string): Promise<TurSNSiteSearchRule[]> {
    const response = await axios.get<TurSNSiteSearchRule[]>(
      `/sn/${snSiteId}/search-rule`,
    );
    return response.data;
  }

  async get(
    snSiteId: string,
    searchRuleId: string,
  ): Promise<TurSNSiteSearchRule> {
    const response = await axios.get<TurSNSiteSearchRule>(
      `/sn/${snSiteId}/search-rule/${searchRuleId}`,
    );
    return response.data;
  }

  async getFieldOptions(
    snSiteId: string,
  ): Promise<TurSNSiteSearchRuleFieldOption[]> {
    const response = await axios.get<TurSNSiteSearchRuleFieldOption[]>(
      `/sn/${snSiteId}/search-rule/fields`,
    );
    return response.data;
  }

  async create(
    snSiteId: string,
    searchRule: TurSNSiteSearchRule,
  ): Promise<TurSNSiteSearchRule> {
    const response = await axios.post<TurSNSiteSearchRule>(
      `/sn/${snSiteId}/search-rule`,
      searchRule,
    );
    return response.data;
  }

  async update(
    snSiteId: string,
    searchRule: TurSNSiteSearchRule,
  ): Promise<TurSNSiteSearchRule> {
    if (!searchRule.id) throw new Error("Search rule id is required.");
    const response = await axios.put<TurSNSiteSearchRule>(
      `/sn/${snSiteId}/search-rule/${searchRule.id}`,
      searchRule,
    );
    return response.data;
  }

  async delete(snSiteId: string, searchRuleId: string): Promise<boolean> {
    const response = await axios.delete<boolean>(
      `/sn/${snSiteId}/search-rule/${searchRuleId}`,
    );
    return response.data === true || response.status === 200;
  }
}
