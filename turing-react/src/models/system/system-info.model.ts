export interface TurDatabaseInfo {
  productName: string;
  productVersion: string;
  driverName: string;
  driverVersion: string;
  url: string;
  status: string;
}

export interface TurMemoryInfo {
  maxMemory: number;
  totalMemory: number;
  usedMemory: number;
  freeMemory: number;
  totalPhysicalMemory: number;
  freePhysicalMemory: number;
  totalSwap: number;
  freeSwap: number;
}

export interface TurDiskInfo {
  totalSpace: number;
  usableSpace: number;
  usedSpace: number;
}

export interface TurExternalServiceInfo {
  enabled: boolean;
  version: string | null;
  endpoint: string | null;
  status: string | null;
}

export interface TurSystemInfo {
  appVersion: string;
  database: TurDatabaseInfo;
  memory: TurMemoryInfo;
  disk: TurDiskInfo;
  mongodb: TurExternalServiceInfo;
  storage: TurExternalServiceInfo;
  storageType: string;
}
