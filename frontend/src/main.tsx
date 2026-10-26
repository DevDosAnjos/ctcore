import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./i18n";
import "./index.css";
import { App } from "./App";
import { BrowserRouter } from "react-router";
import { SessionProvider } from "./features/auth/SessionProvider";
import { ThemeProvider } from "./theme/ThemeProvider";

async function start() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MSW !== "false") {
    const { worker, installE2eHook } = await import("./mocks/browser");
    await worker.start({ onUnhandledFrame: "bypass" });
    installE2eHook(worker);
  }
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <ThemeProvider>
        <BrowserRouter>
          <SessionProvider>
            <App />
          </SessionProvider>
        </BrowserRouter>
      </ThemeProvider>
    </StrictMode>,
  );
}

void start();
