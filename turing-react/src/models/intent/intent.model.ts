export interface TurIntentAction {
  id: string;
  label: string;
  prompt: string;
  sortOrder: number;
}

export interface TurIntent {
  id: string;
  title: string;
  description?: string;
  icon?: string | null;
  enabled: number;
  sortOrder: number;
  actions: TurIntentAction[];
}
