// Espelha os enums Permission e Access do OpenAPI (docs/openapi.yaml). O front só consome níveis; a tabela é do servidor.
export type Access = "FULL" | "READ" | "OWN" | "NOTICE" | "NONE";
export type Permission =
  | "HOME" | "ALL_UNITS_CONSOLIDATED" | "STUDENT_VIEW" | "STUDENT_CONTACTS" | "STUDENT_MANAGE" | "BENEFITS" | "PLANS"
  | "COLLECTIONS" | "CHARGE_ADJUSTMENTS" | "CLASSES_MANAGE" | "AGENDA_VIEW" | "AGENDA_EDIT" | "NO_CLASS_DAYS" | "GRADUATION"
  | "ANNOUNCEMENTS" | "TRIAL_CLASSES" | "TEACHERS" | "STAFF_USERS" | "UNIT_SETTINGS" | "ORGANIZATION_SETTINGS" | "UNITS"
  | "SUBSCRIPTION" | "AUDIT" | "REPORTS" | "EXPORT_LISTS" | "EXPORT_FULL" | "IMPORT" | "SUPPORT_ACCESS";
export type PermissionMap = Record<Permission, Access>;
