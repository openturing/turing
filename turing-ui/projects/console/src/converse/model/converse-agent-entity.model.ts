import { TurSEInstance } from "../../se/model/se-instance.model";

export interface TurConverseAgentEntity {
agent: string;
allowAutomateExpansion: boolean;
fuzzyMatching: boolean;
id: string;
name: string;
synonyms: boolean;
useRegexp: boolean;
}
