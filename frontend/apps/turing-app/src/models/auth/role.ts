export type TurPrivilege = {
  id: string;
  name: string;
  description: string;
  category: string;
};

export type TurRole = {
  id: string;
  name: string;
  description: string;
  turPrivileges: TurPrivilege[];
};
