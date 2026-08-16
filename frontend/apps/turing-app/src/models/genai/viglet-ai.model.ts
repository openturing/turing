/**
 * T957/T958 — device-code pairing with the managed Viglet AI endpoint.
 *
 * The credential itself never reaches the browser: `start` returns the code the
 * operator reads out and the URL they open, and `poll` returns a state plus, on
 * approval, the id of the LLM instance the server already created.
 */

/** Whether this install is already paired, and who it is to the managed service. */
export interface TurVigletPairingStatus {
  paired: boolean;
  llmInstanceId: string | null;
  defaultLlm: boolean;
  installId: string;
}

/** What the console shows the operator while an attempt is open. */
export interface TurVigletPairingSession {
  /** Opaque poll handle — hand it back to `poll`, never display it. */
  deviceCode: string;
  /** Short human-readable code the operator types on the verification page. */
  userCode: string;
  verificationUri: string;
  /** Same URL with the code pre-filled; may be null. */
  verificationUriComplete: string | null;
  intervalSeconds: number;
  expiresInSeconds: number;
}

/** Where the attempt stands. Mirrors the backend `TurVigletPairingState`. */
export type TurVigletPairingState =
  | "PENDING"
  | "SLOW_DOWN"
  | "APPROVED"
  | "DENIED"
  | "EXPIRED";

/** One poll's answer. On `APPROVED` the whole completion already ran server-side. */
export interface TurVigletPairingPoll {
  state: TurVigletPairingState;
  llmInstanceId: string | null;
  defaultLlmSet: boolean;
}
