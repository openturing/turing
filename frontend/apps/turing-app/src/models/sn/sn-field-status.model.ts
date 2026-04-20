import type { TurSNCoreCheck } from "./sn-core-check.model.ts";
import type { TurSNFieldCheck } from "./sn-field-check.model.ts";

export type TurSNStatusFields = {
  cores: TurSNCoreCheck[];
  fields: TurSNFieldCheck[];
};
