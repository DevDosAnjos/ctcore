import { useTranslation } from "react-i18next";

// RN-ACE-08: o servidor é quem decide; aqui só a ajuda ao digitar. Reutilizado pela tela de nova senha (#22).
export const RULE_KEYS = ["length", "number", "upper"] as const;
export type RuleKey = (typeof RULE_KEYS)[number];

export function checkPassword(password: string): Record<RuleKey, boolean> {
  return { length: password.length >= 8, number: /\d/.test(password), upper: /\p{Lu}/u.test(password) };
}

export const isStrongPassword = (password: string) => Object.values(checkPassword(password)).every(Boolean);

export function PasswordRules({ password, id }: { password: string; id?: string }) {
  const { t } = useTranslation();
  const result = checkPassword(password);
  return (
    <div id={id} className="text-sm">
      <p className="font-medium">{t("password.rules.title")}</p>
      <ul aria-live="polite" className="space-y-1">
        {RULE_KEYS.map((key) => (
          <li key={key} className={result[key] ? "text-foreground" : "text-muted-foreground"}>
            {t(`password.rules.${key}`)}: {result[key] ? t("password.rules.met") : t("password.rules.pending")}
          </li>
        ))}
      </ul>
    </div>
  );
}
