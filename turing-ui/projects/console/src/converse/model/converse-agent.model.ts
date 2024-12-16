import { TurSEInstance } from "../../se/model/se-instance.model";

export interface TurConverseAgent {
core: string;
description: string;
id: string;
language: string;
name: string;
turSEInstance: TurSEInstance;
}
