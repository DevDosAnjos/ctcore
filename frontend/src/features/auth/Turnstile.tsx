import { useEffect, useRef } from "react";

interface TurnstileApi {
  render: (el: HTMLElement, opts: Record<string, unknown>) => string;
  reset: (id?: string) => void;
}
declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

const SRC = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

/** RNF-10: desafio invisível da Cloudflare. Sem VITE_TURNSTILE_SITE_KEY não carrega nada. `resetKey` mudando reinicia o desafio. */
export function Turnstile({ onToken, resetKey }: { onToken: (token: string | null) => void; resetKey: number }) {
  const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined;
  const box = useRef<HTMLDivElement>(null);
  const widget = useRef<string>(undefined);
  const first = useRef(true);

  useEffect(() => {
    if (!siteKey || !box.current) return;
    const el = box.current;
    const mount = () => {
      if (!widget.current)
        widget.current = window.turnstile!.render(el, {
          sitekey: siteKey,
          appearance: "interaction-only",
          callback: (t: string) => onToken(t),
          "expired-callback": () => onToken(null),
          "error-callback": () => onToken(null),
        });
    };
    if (window.turnstile) return mount();
    const script = document.createElement("script");
    script.src = SRC;
    script.async = true;
    script.addEventListener("load", mount);
    document.head.appendChild(script);
    return () => script.removeEventListener("load", mount);
  }, [siteKey, onToken]);

  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    onToken(null);
    if (widget.current) window.turnstile?.reset(widget.current);
  }, [resetKey, onToken]);

  return siteKey ? <div ref={box} /> : null;
}
