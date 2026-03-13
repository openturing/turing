export type TurGlobalDecimalSeparator = "DOT" | "COMMA";

export interface TurGlobalSettings {
  decimalSeparator: TurGlobalDecimalSeparator;
  pythonExecutable?: string;
  defaultLlmId?: string;
}
