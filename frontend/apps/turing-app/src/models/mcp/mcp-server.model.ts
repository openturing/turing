export interface TurMcpServer {
  id: string;
  title: string;
  description: string;
  url?: string;
  command?: string;
  args?: string;
  type: "ASYNC" | "SYNC";
  connectionType: "HTTP" | "COMMAND";
  enabled: number;
  icon?: string | null;
}
