import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Card({
  title,
  actions,
  children,
  className,
  heading: Heading = "h3",
}: {
  title: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  /** Nível do título; h3 por padrão, h2 quando o título da tela é um h1. */
  heading?: "h2" | "h3";
}) {
  const id = useId();
  return (
    <section aria-labelledby={id} className={cn("rounded-lg border bg-card p-4 text-card-foreground", className)}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Heading id={id} className="text-base font-semibold">
          {title}
        </Heading>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
      <div className="mt-2">{children}</div>
    </section>
  );
}

// Uma coluna a 360 px, mais colunas conforme a largura (RNF-27).
export function CardGrid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>;
}
