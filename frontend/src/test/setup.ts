import "@testing-library/jest-dom/vitest";
import { configure } from "@testing-library/react";
import "@/i18n";
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest";
import { server } from "@/mocks/server";
import { resetMockSession } from "@/mocks/handlers";
import { setDevicePrefersDark } from "./device";

beforeAll(() => server.listen({ onUnhandledFrame: "error" }));
beforeEach(() => setDevicePrefersDark(false));
afterEach(() => {
  server.resetHandlers();
  resetMockSession();
  window.localStorage.clear();
  document.documentElement.classList.remove("dark");
});
afterAll(() => server.close());

// O fetch do Node nao aceita caminho relativo; no navegador ele e resolvido contra a pagina.
const nodeFetch = globalThis.fetch;
globalThis.fetch = ((input, init) => nodeFetch(typeof input === "string" && input.startsWith("/") ? new URL(input, window.location.origin) : input, init)) as typeof fetch;

// A primeira renderizacao com cobertura e lenta; findBy espera mais que o 1 s padrao.

configure({ asyncUtilTimeout: 5000 });
