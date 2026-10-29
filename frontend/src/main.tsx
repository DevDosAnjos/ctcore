import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/barlow-condensed/latin-500.css";
import "@fontsource/barlow-condensed/latin-600.css";
import "@fontsource/barlow-condensed/latin-700.css";
import "@fontsource/ibm-plex-sans/latin-400.css";
import "@fontsource/ibm-plex-sans/latin-500.css";
import "@fontsource/ibm-plex-sans/latin-600.css";
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
