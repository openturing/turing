export type TurGlobalDecimalSeparator = "DOT" | "COMMA";

export type TurEmailProviderType = "BREVO";

export interface TurGlobalSettings {
  decimalSeparator: TurGlobalDecimalSeparator;
  pythonExecutable?: string;
  defaultLlmId?: string;
  emailProvider?: TurEmailProviderType;
  emailApiKey?: string;
  senderEmail?: string;
  senderName?: string;
  recipientEmail?: string;
}
