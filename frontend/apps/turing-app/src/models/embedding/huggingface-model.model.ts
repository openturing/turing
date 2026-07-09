/**
 * A selectable HuggingFace embedding model for the provider-aware picker
 * (T624/T626). `repoId` is stored on the embedding model's `modelReference`
 * when the `HUGGINGFACE` provider is chosen.
 */
export interface TurHuggingFaceModelOption {
  repoId: string;
  label: string;
  downloads?: number | null;
  likes?: number | null;
  dimensions?: number | null;
  onnxVerified: boolean;
}

/** Where the HuggingFace model list came from, for badging the picker. */
export type TurHuggingFaceModelSource = "LIVE" | "CATALOG" | "NONE";

export interface TurHuggingFaceModelList {
  source: TurHuggingFaceModelSource;
  models: TurHuggingFaceModelOption[];
}
