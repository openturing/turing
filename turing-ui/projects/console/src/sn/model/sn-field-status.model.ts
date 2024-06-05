import {TurSNFieldCheck} from "./sn-field-check.model";
import {TurSNCoreCheck} from "./sn-core-check.model";

export interface TurSNStatusFields {
  cores: TurSNCoreCheck[];
  fields: TurSNFieldCheck[];
}
