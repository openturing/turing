import {TurSNRankingCondition} from "./sn-ranking-condition.model";

export interface TurSNRankingExpression {
  id: string;
  name: string;
  weight: number;
  turSNRankingConditions: TurSNRankingCondition[];
  modificationDate: Date;
  description: string;
}
