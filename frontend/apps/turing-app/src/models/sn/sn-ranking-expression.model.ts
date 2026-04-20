import type {TurSNRankingCondition} from "./sn-ranking-condition.model";

export type TurSNRankingExpression = {
  id: string;
  name: string;
  weight: number;
  turSNRankingConditions: TurSNRankingCondition[];
  lastModifiedDate: Date;
  description: string;
}
