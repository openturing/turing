import type {TurSNRankingExpression} from "@/models/sn/sn-ranking-expression.model";
import axios from "axios";

export class TurSNRankingExpressionService {

    async query(siteId: string): Promise<TurSNRankingExpression[]> {
        const response = await axios.get<TurSNRankingExpression[]>(`/sn/${siteId}/ranking-expression`);
        return response.data;
    }

    async get(siteId: string, id: string): Promise<TurSNRankingExpression> {
        const response = await axios.get<TurSNRankingExpression>(
            `/sn/${siteId}/ranking-expression/${id}`
        );
        return response.data;
    }

    async create(siteId: string, turSNRankingExpression: TurSNRankingExpression): Promise<TurSNRankingExpression> {
        const response = await axios.post<TurSNRankingExpression>(
            `/sn/${siteId}/ranking-expression`,
            turSNRankingExpression
        );
        return response.data;
    }

    async update(siteId: string, turSNRankingExpression: TurSNRankingExpression): Promise<TurSNRankingExpression> {
        const response = await axios.put<TurSNRankingExpression>(
            `/sn/${siteId}/ranking-expression/${turSNRankingExpression.id.toString()}`,
            turSNRankingExpression
        );
        return response.data;
    }

    async delete(siteId: string, turSNRankingExpression: TurSNRankingExpression): Promise<boolean> {
        const response = await axios.delete<TurSNRankingExpression>(
            `/sn/${siteId}/ranking-expression/${turSNRankingExpression.id.toString()}`
        );
        return response.status == 200;
    }
}
