import type { Permission } from "./types";

export interface CatalogItem {
  id: string;
  permission: Permission;
  href: string;
  /** D7: só vira true no item que entrega a tela; o menu nunca aponta para endereço inexistente. */
  available: boolean;
  /** Ícone (lucide-react) do menu lateral; o mapa nome -> componente fica em `components/layout/icons.ts`. */
  icon: string;
}
export interface CatalogGroup {
  id: string;
  items: CatalogItem[];
}

const item = (id: string, permission: Permission, href: string, icon: string, available = false): CatalogItem => ({ id, permission, href, icon, available });

// Nomes em pt-BR.json: menu.groups.<id> e menu.items.<id>.
export const menuCatalog: CatalogGroup[] = [
  { id: "home", items: [item("home", "HOME", "/", "House", true)] },
  {
    id: "operation",
    items: [
      item("agenda", "AGENDA_VIEW", "/agenda", "CalendarDays", true),
      item("classes", "CLASSES_MANAGE", "/turmas", "LayoutGrid", true),
      item("noClassDays", "NO_CLASS_DAYS", "/dias-sem-aula", "CalendarOff", true),
      item("graduation", "GRADUATION", "/graduacao", "Award"),
      item("trialClasses", "TRIAL_CLASSES", "/aulas-experimentais", "Ticket"),
      item("announcements", "ANNOUNCEMENTS", "/comunicados", "Megaphone"),
    ],
  },
  {
    id: "people",
    items: [
      item("students", "STUDENT_MANAGE", "/alunos", "Users", true),
      item("requests", "STUDENT_MANAGE", "/pedidos", "Inbox", true),
      item("teachers", "TEACHERS", "/professores", "UserCheck", true),
    ],
  },
  {
    id: "finance",
    items: [
      item("collections", "COLLECTIONS", "/cobrancas", "Wallet"),
      item("plans", "PLANS", "/planos", "Layers", true),
      item("benefits", "BENEFITS", "/beneficios", "Gift", true),
    ],
  },
  {
    id: "reports",
    items: [item("reports", "REPORTS", "/relatorios", "ChartColumn"), item("allUnits", "ALL_UNITS_CONSOLIDATED", "/todas-as-unidades", "Building2")],
  },
  {
    id: "settings",
    items: [
      item("unitSettings", "UNIT_SETTINGS", "/configuracoes/unidade", "Settings", true),
      item("organizationSettings", "ORGANIZATION_SETTINGS", "/configuracoes/organizacao", "Building", true),
      item("modalities", "ORGANIZATION_SETTINGS", "/configuracoes/modalidades", "Dumbbell", true),
      item("ageRanges", "ORGANIZATION_SETTINGS", "/configuracoes/faixas-etarias", "Baby", true),
      item("terms", "ORGANIZATION_SETTINGS", "/configuracoes/termos", "FileText", true),
      item("texts", "ORGANIZATION_SETTINGS", "/configuracoes/textos", "Type", true),
      item("staffUsers", "STAFF_USERS", "/configuracoes/usuarios", "UserCog", true),
      item("units", "UNITS", "/configuracoes/unidades", "MapPin", true),
      item("subscription", "SUBSCRIPTION", "/configuracoes/assinatura", "CreditCard"),
      item("audit", "AUDIT", "/configuracoes/auditoria", "ScrollText"),
      item("import", "IMPORT", "/configuracoes/importar", "Upload"),
      item("exportFull", "EXPORT_FULL", "/configuracoes/exportacao", "Download"),
      item("support", "SUPPORT_ACCESS", "/configuracoes/suporte", "LifeBuoy"),
    ],
  },
];
