import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils";

const BOX = "rounded-card border bg-card text-card-foreground";

type Heading = "h2" | "h3";

/** Cartão com título e ação à direita; o título nomeia a região. */
export function Section({
  title,
  action,
  children,
  className,
  heading: H = "h3",
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  /** Nível do título; h3 por padrão, h2 quando o título da tela é um h1. */
  heading?: Heading;
}) {
  const id = useId();
  return (
    <section aria-labelledby={id} className={cn(BOX, "p-4", className)}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <H id={id} className="text-base font-semibold">
          {title}
        </H>
        {action && <div className="flex flex-wrap gap-2">{action}</div>}
      </div>
      <div className="mt-2">{children}</div>
    </section>
  );
}

/** Sem título, só o contêiner; com título, é uma `Section` (`actions` é o nome antigo de `action`). */
export function Card({
  title,
  actions,
  children,
  className,
  heading,
}: {
  title?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  heading?: Heading;
}) {
  if (title === undefined) return <div className={cn(BOX, "p-4", className)}>{children}</div>;
  return (
    <Section title={title} action={actions} className={className} heading={heading}>
      {children}
    </Section>
  );
}

// Uma coluna a 360 px, mais colunas conforme a largura (RNF-27).
export function CardGrid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>;
}
