import * as React from "react";
import { cn } from "@/lib/utils";

export type TabItem = { id: string; label: string; content: React.ReactNode };

/** Abas com teclado (setas, Home, End); só o painel ativo é exibido. */
export function Tabs({ tabs, label, defaultId, className }: { tabs: TabItem[]; label: string; defaultId?: string; className?: string }) {
  const base = React.useId();
  const [active, setActive] = React.useState(defaultId ?? tabs[0]?.id);
  const refs = React.useRef<Record<string, HTMLButtonElement | null>>({});
  const go = (i: number) => {
    const next = tabs[(i + tabs.length) % tabs.length];
    setActive(next.id);
    refs.current[next.id]?.focus();
  };
  const onKeyDown = (e: React.KeyboardEvent, i: number) => {
    const to = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: tabs.length - 1 }[e.key];
    if (to === undefined) return;
    e.preventDefault();
    go(to);
  };
  return (
    <div className={className}>
      <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b border-divider">
        {tabs.map((tab, i) => (
          <button
            key={tab.id}
            ref={(el) => {
              refs.current[tab.id] = el;
            }}
            type="button"
            role="tab"
            id={`${base}-t-${tab.id}`}
            aria-selected={tab.id === active}
            aria-controls={`${base}-p-${tab.id}`}
            tabIndex={tab.id === active ? 0 : -1}
            onClick={() => setActive(tab.id)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={cn(
              "relative -mb-px min-h-11 whitespace-nowrap border-b-2 px-3 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              tab.id === active ? "border-brand text-foreground" : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {tabs.map(
        (tab) =>
          tab.id === active && (
            <div key={tab.id} role="tabpanel" id={`${base}-p-${tab.id}`} aria-labelledby={`${base}-t-${tab.id}`} className="pt-4">
              {tab.content}
            </div>
          ),
      )}
    </div>
  );
}
