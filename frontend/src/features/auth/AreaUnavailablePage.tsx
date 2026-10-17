import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { buttonVariants } from "@/components/ui/button";
import { ChooseLinkTrigger } from "./ChooseLinkTrigger";

// RF-ACE-08: texto único para qualquer área negada (D3); a recusa de dados é do servidor (403).
export function AreaUnavailablePage() {
  const { t } = useTranslation();
  const title = useRef<HTMLHeadingElement>(null);
  useEffect(() => title.current?.focus(), []);
  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-4 px-4 py-6">
      <h1 ref={title} tabIndex={-1} className="text-2xl font-semibold outline-none">
        {t("areaUnavailable.title")}
      </h1>
      <p className="text-muted-foreground">{t("areaUnavailable.text")}</p>
      <div className="flex flex-wrap items-center gap-2">
        <Link to="/" className={buttonVariants({ size: "sm" })}>
          {t("areaUnavailable.home")}
        </Link>
        <ChooseLinkTrigger />
      </div>
    </main>
  );
}
