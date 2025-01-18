import {TurChatMetadata} from "./sn-chat-metadata-model";

export interface TurSNChat {
  messageType: string;
  metadata: TurChatMetadata;
  toolCalls: string[];
  media: string[];
  content: string;
  text: string;
}
