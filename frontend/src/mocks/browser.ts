import { HttpResponse, http, type JsonBodyType } from "msw";
import { setupWorker } from "msw/browser";
import { handlers } from "./handlers";

export const worker = setupWorker(...handlers);

type Override = { method: "get" | "post" | "put" | "patch" | "delete"; path: string; status: number; body: JsonBodyType };

declare global {
  interface Window {
    __mswQueue?: Override[];
    __mswOverride?: (spec: Override) => void;
    __mswReset?: () => void;
  }
}

// Gancho do e2e (só em dev com MSW, ver main.tsx): troca a resposta de um endpoint por teste, sem mexer nos handlers padrão.
export function installE2eHook(w: typeof worker) {
  const override = ({ method, path, status, body }: Override) => w.use(http[method](`/api/v1${path}`, () => HttpResponse.json(body, { status })));
  window.__mswOverride = override;
  window.__mswReset = () => w.resetHandlers();
  window.__mswQueue?.forEach(override);
}
