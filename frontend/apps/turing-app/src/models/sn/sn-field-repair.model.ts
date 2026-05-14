export type TurSNFieldRepairType =
  | "SE_CREATE_FIELD"
  | "SE_CHANGE_TYPE"
  | "SE_ENABLE_MULTI_VALUE"
  | "SN_CHANGE_TYPE"
  | "REPAIR_ALL";

export type TurSNFieldRepairPayload = {
  id: string;
  core: string;
  repairType: TurSNFieldRepairType;
  value?: string;
};
