import "@testing-library/jest-dom/vitest";
import "@/i18n";
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest";
import { server } from "@/mocks/server";
import { setDevicePrefersDark } from "./device";

beforeAll(() => server.listen({ onUnhandledFrame: "error" }));
beforeEach(() => setDevicePrefersDark(false));
afterEach(() => {
  server.resetHandlers();
  window.localStorage.clear();
  document.documentElement.classList.remove("dark");
});
afterAll(() => server.close());
