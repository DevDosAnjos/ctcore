import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { Loader2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";

// Altura visual do canvas (32/40/48 px); o alvo de toque de 44 px (RNF-20, D2) vem da área
// invisível do `after:`, que cresce até 44 px quando o botão é menor.
export const buttonVariants = cva(
  "relative inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors after:absolute after:left-1/2 after:top-1/2 after:h-full after:min-h-11 after:w-full after:min-w-11 after:-translate-x-1/2 after:-translate-y-1/2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 aria-disabled:cursor-not-allowed aria-disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        primary: "bg-primary text-primary-foreground hover:bg-primary/90",
        secondary: "border border-input bg-card text-foreground hover:bg-surface-soft",
        ghost: "text-foreground hover:bg-accent hover:text-accent-foreground",
        danger: "border border-danger-foreground/40 bg-card text-danger-foreground hover:bg-danger",
        dangerSolid: "bg-destructive text-destructive-foreground hover:bg-destructive/90",
        accent: "bg-brand text-destructive-foreground hover:bg-brand/90",
        whatsapp: "border border-ok-foreground/40 bg-ok text-ok-foreground hover:bg-ok/80",
        // Nomes antigos (D1): continuam aceitos com o visual novo.
        default: "bg-primary text-primary-foreground hover:bg-primary/90",
        outline: "border border-input bg-card text-foreground hover:bg-surface-soft",
      },
      size: {
        sm: "h-8 px-3",
        md: "h-10 px-4",
        lg: "h-12 px-6 text-base",
        default: "h-10 px-4",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: { variant: "primary", size: "md" },
  },
);

export interface ButtonProps extends React.ComponentProps<"button">, VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  /** Ícone antes do texto. */
  icon?: React.ReactNode;
  /** Sem clique, `aria-busy` e indicador anunciado. */
  loading?: boolean;
  /** Com `disabled`, mantém o botão focável e explica o motivo. */
  disabledReason?: string;
}

export function Button({
  className,
  variant,
  size,
  asChild = false,
  icon,
  loading,
  disabledReason,
  children,
  onClick,
  disabled,
  ...rest
}: ButtonProps) {
  const { t } = useTranslation();
  const reasonId = React.useId();
  const classes = cn(buttonVariants({ variant, size, className }));
  if (asChild) {
    return (
      <Slot className={classes} onClick={onClick} {...rest}>
        {children}
      </Slot>
    );
  }
  const explained = Boolean(disabled && disabledReason);
  const blocked = Boolean(loading) || explained;
  return (
    <button
      {...rest}
      className={classes}
      disabled={explained ? undefined : disabled}
      aria-disabled={blocked || undefined}
      aria-busy={loading || undefined}
      aria-describedby={explained ? reasonId : rest["aria-describedby"]}
      title={explained ? disabledReason : rest.title}
      onClick={blocked ? undefined : onClick}
    >
      {loading ? <Loader2 aria-hidden className="animate-spin" /> : icon}
      {loading && <span className="sr-only">{t("ui.loading")}</span>}
      {children}
      {explained && (
        <span id={reasonId} className="sr-only">
          {disabledReason}
        </span>
      )}
    </button>
  );
}
