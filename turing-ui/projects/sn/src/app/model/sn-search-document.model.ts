import { TurSNSearchDocumentField } from "./sn-search-document-field.model";
import { TurSNSearchDocumentMetadata } from "./sn-search-document-metadata.model";

export interface TurSNSearchDocument {
 elevate: boolean;
 fields: TurSNSearchDocumentField;
 metadata: TurSNSearchDocumentMetadata[];
}
