import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ReauthDialog } from "@/features/auth/ReauthDialog";
import { useSession, type SessionView } from "@/features/auth/SessionProvider";
import { ApiError, api } from "@/lib/api";

type Phase = "form" | "reauth" | "sending" | "failed" | "forbidden";

// RF-ORG-06 / RNF-26: resumo, e-mail do novo Coordenador e palavra digitada; depois a senha; só então a rota.
export function TransferCoordinationDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const { setSession } = useSession();
  const [email, setEmail] = useState("");
  const [word, setWord] = useState("");
  const [phase, setPhase] = useState<Phase>("form");
  const [errors, setErrors] = useState<{ email?: string; confirmation?: string }>({});
  const ready = email.trim() !== "" && word.trim().toUpperCase() === "TRANSFERIR";

  function close() {
    setEmail("");
    setWord("");
    setErrors({});
    setPhase("form");
    onClose();
  }

  async function send(token: string) {
    setPhase("sending");
    try {
      await api("/organization/coordination-transfer", {
        method: "POST",
        body: { email: email.trim(), confirmation: word.trim() },
        headers: { "X-Reauth-Token": token },
      });
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        const field = (name: string) => e.fields.find((f) => f.field === name) && t(`organization.transfer.${name}Invalid`);
        setErrors({ email: field("email") || undefined, confirmation: field("confirmation") || undefined });
        setPhase("form");
      } else setPhase(e instanceof ApiError && e.status === 403 ? "forbidden" : "failed");
      return;
    }
    // A sessão é recarregada sem passar por "carregando", para a tela e o aviso não serem refeitos.
    try {
      setSession(await api<SessionView>("/auth/session"));
    } catch {
      // o perfil novo aparece na próxima leitura da sessão
    }
    close();
    onDone();
  }

  return (
    <>
      <Modal open={open && phase !== "reauth"} onOpenChange={(o) => !o && phase !== "sending" && close()} title={t("organization.transfer.title")}>
        {phase === "forbidden" ? (
          <ForbiddenState />
        ) : phase === "failed" ? (
          <ErrorState onRetry={() => setPhase("reauth")} />
        ) : (
          <form
            noValidate
            className="flex flex-col gap-3"
            aria-busy={phase === "sending"}
            onSubmit={(e) => {
              e.preventDefault();
              if (ready && phase === "form") setPhase("reauth");
            }}
          >
            <Banner tone="warn">{t("organization.transfer.summary")}</Banner>
            <Field label={t("organization.transfer.email")} hint={errors.email ? undefined : t("organization.transfer.emailHelp")} error={errors.email} alertError>
              {(c) => <Input {...c} type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />}
            </Field>
            <Field label={t("organization.transfer.typeLabel")} error={errors.confirmation} alertError>
              {(c) => <Input {...c} autoComplete="off" value={word} onChange={(e) => setWord(e.target.value)} />}
            </Field>
            <div className="flex flex-wrap justify-end gap-2">
              <Button type="button" variant="secondary" className="min-h-11" onClick={close} disabled={phase === "sending"}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" className="min-h-11" disabled={!ready || phase === "sending"}>
                {t("organization.transfer.continue")}
              </Button>
            </div>
          </form>
        )}
      </Modal>
      <ReauthDialog open={open && phase === "reauth"} action="TRANSFER_COORDINATION" onClose={() => setPhase("form")} onConfirmed={(token) => void send(token)} />
    </>
  );
}
