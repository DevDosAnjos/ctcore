import type { NavTab } from "@/components/layout/MobileTabs";
import { menuCatalog } from "./menuCatalog";
import type { PermissionMap } from "./types";

// D5/D6: abas do celular por perfil; a aba some sem permissão (a mesma do menu) e fica desabilitada se a tela ainda não existe.
const IDS = { default: ["home", "students", "agenda", "collections"], TEACHER: ["home", "agenda", "announcements"] };

export function buildTabs(permissions: PermissionMap, role: string | undefined, t: (key: string) => string): NavTab[] {
  const all = menuCatalog.flatMap((g) => g.items);
  return (role === "TEACHER" ? IDS.TEACHER : IDS.default).flatMap((id) => {
    const i = all.find((x) => x.id === id);
    if (!i || permissions[i.permission] === "NONE") return [];
    return [{ id, label: t(`layout.tabs.${id}`), href: i.href, icon: i.icon, disabled: !i.available }];
  });
}
