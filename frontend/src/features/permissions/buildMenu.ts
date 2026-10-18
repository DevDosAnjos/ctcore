import type { NavGroup } from "@/components/layout/AppLayout";
import { menuCatalog, type CatalogGroup } from "./menuCatalog";
import type { PermissionMap } from "./types";

// R7: só entram itens disponíveis cujo nível não é NONE, e grupos que ficarem com algum item; ordem do catálogo.
export function buildMenu(
  permissions: PermissionMap,
  t: (key: string) => string,
  catalog: CatalogGroup[] = menuCatalog,
): NavGroup[] {
  return catalog
    .map((g) => ({
      id: g.id,
      label: t(`menu.groups.${g.id}`),
      items: g.items
        .filter((i) => i.available && permissions[i.permission] !== "NONE")
        .map((i) => ({ id: i.id, label: t(`menu.items.${i.id}`), href: i.href })),
    }))
    .filter((g) => g.items.length > 0);
}
