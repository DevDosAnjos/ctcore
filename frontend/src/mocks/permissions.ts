import type { Access, Permission, PermissionMap } from "@/features/permissions/types";

// Espelha a tabela do servidor só para o mock (docs/especificacao/02-contas-e-acesso.md): [Coordenador, Administrador, Professor].
// Professor com as cinco permissões da unidade ligadas; no padrão do servidor as quatro primeiras vêm ligadas e "ver ficha do aluno" desligada.
const F: Access = "FULL";
const R: Access = "READ";
const N: Access = "NONE";
const rows: Record<Permission, [Access, Access, Access]> = {
  HOME: [F, F, F], ALL_UNITS_CONSOLIDATED: [R, N, N], STUDENT_VIEW: [F, F, F], STUDENT_RECORD: [F, F, F], STUDENT_CONTACTS: [F, F, R],
  STUDENT_MANAGE: [F, F, N], BENEFITS: [F, F, N], PLANS: [F, F, N], COLLECTIONS: [F, F, N], CHARGE_ADJUSTMENTS: [F, F, N],
  CLASSES_MANAGE: [F, F, N], AGENDA_VIEW: [F, F, F], AGENDA_EDIT: [F, F, F], NO_CLASS_DAYS: [F, F, N], GRADUATION: [F, F, R],
  ANNOUNCEMENTS: [F, F, F], TRIAL_CLASSES: [F, F, R], TEACHERS: [F, F, N], STAFF_USERS: [F, F, N], UNIT_SETTINGS: [F, F, N],
  ORGANIZATION_SETTINGS: [F, R, N], UNITS: [F, N, N], SUBSCRIPTION: [F, "NOTICE", N], AUDIT: [F, F, N], REPORTS: [F, F, "OWN"],
  EXPORT_LISTS: [F, F, N], EXPORT_FULL: [F, N, N], IMPORT: [F, F, N], SUPPORT_ACCESS: [F, N, N],
};
const column: Record<string, number> = { COORDINATOR: 0, ADMINISTRATOR: 1, TEACHER: 2 };

/** Portal e papel desconhecido: tudo NONE, como no servidor. */
export function mockPermissions(role: string | undefined): PermissionMap {
  const c = role === undefined ? undefined : column[role];
  return Object.fromEntries(
    Object.entries(rows).map(([code, levels]) => [code, c === undefined ? N : levels[c]]),
  ) as PermissionMap;
}
