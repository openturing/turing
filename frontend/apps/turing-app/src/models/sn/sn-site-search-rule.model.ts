export type TurSNSiteSearchRuleParameter = "QUERY" | "FILTER_QUERY" | "SORT" | "LOCALE";
export type TurSNSiteSearchRuleOperator = "EQUALS" | "CONTAINS" | "STARTS_WITH" | "MATCHES_ANY" | "IS_EMPTY";
export type TurSNSiteSearchRuleActionType = "SET_SORT" | "ADD_FACETS" | "REMOVE_FACETS" | "SET_ROWS" | "ADD_FILTER_QUERY" | "SET_BOOST_QUERY";
export type TurSNSiteSearchRuleLogicOperator = "AND" | "OR";

export interface TurSNSiteSearchRuleCondition {
  id?: string;
  parameter: TurSNSiteSearchRuleParameter;
  operator: TurSNSiteSearchRuleOperator;
  fieldName?: string;
  value?: string;
  logicOperator: TurSNSiteSearchRuleLogicOperator;
}

export interface TurSNSiteSearchRuleAction {
  id?: string;
  actionType: TurSNSiteSearchRuleActionType;
  value: string;
}

export interface TurSNSiteSearchRule {
  id?: string;
  name: string;
  description?: string;
  position: number;
  enabled: boolean;
  conditions: TurSNSiteSearchRuleCondition[];
  actions: TurSNSiteSearchRuleAction[];
}

export interface TurSNSiteSearchRuleFieldOption {
  id: string;
  name: string;
  type?: string;
  facet?: boolean;
  facetName?: string;
}
