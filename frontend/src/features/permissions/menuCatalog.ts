import type { Permission } from "./types";

export interface CatalogItem {
  id: string;
  permission: Permission;
  href: string;
  /** D7: só vira true no item que entrega a tela; o menu nunca aponta para endereço inexistente. */
  available: boolean;
}
export interface CatalogGroup {
  id: string;
  items: CatalogItem[];
}

const item = (id: string, permission: Permission, href: string, available = false): CatalogItem => ({ id, permission, href, available });

// Nomes em pt-BR.json: menu.groups.<id> e menu.items.<id>.
export const menuCatalog: CatalogGroup[] = [
  { id: "home", items: [item("home", "HOME", "/", true)] },
  {
    id: "operation",
    items: [
      item("agenda", "AGENDA_VIEW", "/agenda"),
      item("classes", "CLASSES_MANAGE", "/turmas"),
      item("noClassDays", "NO_CLASS_DAYS", "/dias-sem-aula"),
      item("graduation", "GRADUATION", "/graduacao"),
      item("trialClasses", "TRIAL_CLASSES", "/aulas-experimentais"),
      item("announcements", "ANNOUNCEMENTS", "/comunicados"),
    ],
  },
  {
    id: "people",
    items: [item("students", "STUDENT_VIEW", "/alunos"), item("teachers", "TEACHERS", "/professores", true)],
  },
  {
    id: "finance",
    items: [
      item("collections", "COLLECTIONS", "/cobrancas"),
      item("plans", "PLANS", "/planos"),
      item("benefits", "BENEFITS", "/beneficios"),
    ],
  },
  {
    id: "reports",
    items: [item("reports", "REPORTS", "/relatorios"), item("allUnits", "ALL_UNITS_CONSOLIDATED", "/todas-as-unidades")],
  },
  {
    id: "settings",
    items: [
      item("unitSettings", "UNIT_SETTINGS", "/configuracoes/unidade", true),
      item("organizationSettings", "ORGANIZATION_SETTINGS", "/configuracoes/organizacao", true),
      item("modalities", "ORGANIZATION_SETTINGS", "/configuracoes/modalidades", true),
      item("ageRanges", "ORGANIZATION_SETTINGS", "/configuracoes/faixas-etarias", true),
      item("staffUsers", "STAFF_USERS", "/configuracoes/usuarios", true),
      item("units", "UNITS", "/configuracoes/unidades", true),
      item("subscription", "SUBSCRIPTION", "/configuracoes/assinatura"),
      item("audit", "AUDIT", "/configuracoes/auditoria"),
      item("import", "IMPORT", "/configuracoes/importar"),
      item("exportFull", "EXPORT_FULL", "/configuracoes/exportacao"),
      item("support", "SUPPORT_ACCESS", "/configuracoes/suporte"),
    ],
  },
];
