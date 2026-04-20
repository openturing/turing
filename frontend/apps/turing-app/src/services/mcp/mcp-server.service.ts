import axios from "axios";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts";

export class TurMcpServerService {
  async query(): Promise<TurMcpServer[]> {
    const response = await axios.get<TurMcpServer[]>("/mcp");
    return response.data;
  }
  async get(id: string): Promise<TurMcpServer> {
    const response = await axios.get<TurMcpServer>(`/mcp/${id}`);
    return response.data;
  }
  async create(turMcpServer: TurMcpServer): Promise<TurMcpServer> {
    const response = await axios.post<TurMcpServer>("/mcp", turMcpServer);
    return response.data;
  }
  async update(turMcpServer: TurMcpServer): Promise<TurMcpServer> {
    const response = await axios.put<TurMcpServer>(
      `/mcp/${turMcpServer.id.toString()}`,
      turMcpServer
    );
    return response.data;
  }
  async delete(turMcpServer: TurMcpServer): Promise<boolean> {
    const response = await axios.delete<TurMcpServer>(
      `/mcp/${turMcpServer.id.toString()}`
    );
    return response.status == 200;
  }
}
