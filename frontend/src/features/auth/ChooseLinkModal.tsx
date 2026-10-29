import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { api, ApiError } from "@/lib/api";
import { useSession, type SessionView } from "./SessionProvider";

type Link = SessionView["links"][number];
const PORTAL = ["STUDENT", "GUARDIAN"];

// RF-ACE-05: a área vem do papel do vínculo (D2). O servidor decide se o vínculo é da pessoa.
export function ChooseLinkModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose?: () => void;
}) {
  const { t } = useTranslation();
  const { state, setSession } = useSession();
  const [pending, setPending] = useState<Link | null>(null);
  const [error, setError] = useState<"failed" | "gone" | null>(null);
  const [retryLink, setRetry] = useState<Link | null>(null);
  if (state.status !== "ready") return null;
  const { links, activeLinkId } = state.session;

  async function choose(link: Link) {
    setPending(link);
    setError(null);
    try {
      const view = await api<SessionView>("/auth/session/link", {
        method: "PUT",
        body: { linkId: link.id },
      });
      setPending(null);
      setSession(view);
      onClose?.();
    } catch (e) {
      setPending(null);
      if (e instanceof ApiError && e.status === 404) {
        setError("gone");
        try {
          setSession(await api<SessionView>("/auth/session"));
        } catch {
          /* a lista antiga fica; a pessoa pode tentar de novo */
        }
      } else {
        setError("failed");
        setRetry(link);
      }
    }
  }

  return (
    <Modal
      open={open}
      onOpenChange={(o) => {
        if (o || pending) return;
        setError(null);
        onClose?.();
      }}
      title={t("chooseLink.title")}
      dismissible={!!onClose}
    >
      <p className="mb-3 text-sm text-muted-foreground">
        {t("chooseLink.intro")}
      </p>
      <ul className="flex flex-col gap-2">
        {links.map((l) => (
          <li key={l.id}>
            <Button
              variant="outline"
              className="h-auto min-h-14 w-full flex-col items-start gap-0 whitespace-normal py-2 text-left"
              disabled={!!pending}
              aria-current={l.id === activeLinkId ? "true" : undefined}
              onClick={() => void choose(l)}
            >
              <span className="text-lg font-semibold uppercase leading-tight">{l.organizationName}</span>
              {" "}
              <span className="font-normal">
                {t("chooseLink.areaOption", {
                  area: t(
                    PORTAL.includes(l.role)
                      ? "chooseLink.areas.portal"
                      : "chooseLink.areas.management",
                  ),
                  role: t(`chooseLink.roles.${l.role}`),
                })}
                {l.id === activeLinkId && ` · ${t("chooseLink.current")}`}
              </span>
            </Button>
          </li>
        ))}
      </ul>
      {pending && (
        <p role="status" className="mt-3 text-sm text-muted-foreground">
          {t("chooseLink.sending")}
        </p>
      )}
      {error && (
        <div role="alert" className="mt-3 flex flex-col gap-2 text-sm">
          <p>
            {t(
              error === "gone"
                ? "chooseLink.errorGone"
                : "chooseLink.errorFailed",
            )}
          </p>
          {error === "failed" && retryLink && (
            <Button
              className="min-h-11 self-start"
              onClick={() => void choose(retryLink)}
            >
              {t("chooseLink.retry")}
            </Button>
          )}
        </div>
      )}
    </Modal>
  );
}
