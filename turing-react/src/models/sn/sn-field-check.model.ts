import type { TurSNFieldCoreCheck } from "./sn-field-core-check.model.ts";

export type TurSNFieldCheck = {
  id: string;
  externalId: string;
  name: string;
  facetIsCorrect: boolean;
  correct: boolean;
  cores: TurSNFieldCoreCheck[];
};
