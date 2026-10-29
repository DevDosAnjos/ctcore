import { useState, type Ref } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { PasswordRules } from "./PasswordRules";

interface Props {
  password: string;
  confirm: string;
  onPassword: (value: string) => void;
  onConfirm: (value: string) => void;
  /** Erro vindo do servidor para o campo de senha (vazamento ou senha fraca). */
  error?: string | null;
  inputRef?: Ref<HTMLInputElement>;
  /** Prefixo dos ids, para não repetir na mesma página. */
  idPrefix?: string;
}

// Senha nova com confirmação, exigências marcadas ao digitar e Mostrar/Ocultar (RN-ACE-08). Reutilizável pela #22.
export function PasswordFields({ password, confirm, onPassword, onConfirm, error, inputRef, idPrefix = "pw" }: Props) {
  const { t } = useTranslation();
  const [show, setShow] = useState(false);
  const type = show ? "text" : "password";
  const mismatch = confirm !== "" && confirm !== password;
  return (
    <>
      <Field id={`${idPrefix}-senha`} label={t("password.label")} error={error ?? undefined}>
        {(c) => (
          <div className="flex gap-2">
            <Input
              {...c}
              ref={inputRef}
              type={type}
              autoComplete="new-password"
              value={password}
              onChange={(e) => onPassword(e.target.value)}
              aria-describedby={[c["aria-describedby"], `${idPrefix}-regras`].filter(Boolean).join(" ")}
            />
            <Button type="button" variant="secondary" onClick={() => setShow((s) => !s)}>
              {show ? t("password.hide") : t("password.show")}
            </Button>
          </div>
        )}
      </Field>
      <PasswordRules id={`${idPrefix}-regras`} password={password} />
      <Field id={`${idPrefix}-confirmar`} label={t("password.confirm")} error={mismatch ? t("password.mismatch") : undefined}>
        {(c) => <Input {...c} type={type} autoComplete="new-password" value={confirm} onChange={(e) => onConfirm(e.target.value)} />}
      </Field>
    </>
  );
}
