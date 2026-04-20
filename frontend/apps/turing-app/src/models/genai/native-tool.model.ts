export interface NativeToolDescriptor {
  name: string;
  description: string;
  groupId: string;
}

export interface NativeToolGroup {
  id: string;
  title: string;
  tools: NativeToolDescriptor[];
}
