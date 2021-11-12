import { TurSNSearchSpellCheckText } from "./sn-search-spell-check-text.model";

export interface TurSNSearchSpellCheck {
  original: TurSNSearchSpellCheckText;
  corrected: TurSNSearchSpellCheckText;
  correctedText: boolean;
}
