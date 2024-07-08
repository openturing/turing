import {TurSNFieldCoreCheck} from "./sn-field-core-check.model";

export interface TurSNFieldCheck {
  id: string;
  externalId: string;
  name: string
  facetIsCorrect: boolean;
  correct: boolean;
  cores: TurSNFieldCoreCheck[];
}
