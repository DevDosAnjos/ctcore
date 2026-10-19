import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import {
  useGraduation,
  type Graduation,
  type GraduationUpdate,
} from "./useGraduation";

const MAX_LEVELS = 30;
const MAX_NAME = 40;
const LEVELS_ERROR = "graduation-levels-error";
const LEVELS = "levels";
const input = "min-h-11 w-full rounded-md border bg-background px-2";

// Linha da tela: números ficam em texto até salvar.
type Row = {
  key: string;
  id?: string;
  name: string;
  color: string;
  secondaryColor: string;
  maxDegree: string;
  minAge: string;
  maxAge: string;
  kids: boolean;
};

const toRows = (g: Graduation): Row[] =>
  g.levels.map((l) => ({
    key: l.id,
    id: l.id,
    name: l.name,
    color: l.color,
    secondaryColor: l.secondaryColor ?? "",
    maxDegree: String(l.maxDegree),
    minAge: l.minAge === null ? "" : String(l.minAge),
    maxAge: l.maxAge === null ? "" : String(l.maxAge),
    kids: l.kids,
  }));

const num = (s: string) => (s.trim() === "" ? null : Number(s));

function ReadOnly({ data }: { data: Graduation }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-3">
      <p>
        {data.enabled
          ? t("modalities.graduation.statusOn")
          : t("modalities.graduation.statusOff")}
      </p>
      <p>
        {data.requireOriginProof
          ? t("modalities.graduation.proofOn")
          : t("modalities.graduation.proofOff")}
      </p>
      <ol className="flex flex-col divide-y">
        {data.levels.map((l) => (
          <li key={l.id} className="flex flex-wrap items-center gap-2 py-2">
            <span
              aria-hidden="true"
              className="inline-block size-5 rounded border"
              style={{ backgroundColor: l.color }}
            />
            <span className="min-w-0 break-words font-medium">{l.name}</span>
            <span className="text-sm text-muted-foreground">
              {t("modalities.graduation.summary", {
                degrees: l.maxDegree,
                kind: l.kids
                  ? t("modalities.graduation.kidsShort")
                  : t("modalities.graduation.adultShort"),
              })}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}

function Editor({
  data,
  save,
  onSaved,
  onClose,
}: {
  data: Graduation;
  save: (body: GraduationUpdate) => Promise<Graduation>;
  onSaved: (g: Graduation) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [enabled, setEnabled] = useState(data.enabled);
  const [proof, setProof] = useState(data.requireOriginProof);
  const [rows, setRows] = useState<Row[]>(() => toRows(data));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const patch = (i: number, change: Partial<Row>) => {
    setErrors({});
    setRows((all) => all.map((r, j) => (j === i ? { ...r, ...change } : r)));
  };
  const move = (i: number, by: number) => {
    setErrors({});
    setRows((all) => {
      const next = [...all];
      [next[i], next[i + by]] = [next[i + by], next[i]];
      return next;
    });
  };

  async function submit() {
    if (busy) return;
    const found: Record<string, string> = {};
    rows.forEach((r, i) => {
      const name = r.name.trim();
      if (name.length < 1 || name.length > MAX_NAME)
        found[`levels[${i}].name`] = t("modalities.graduation.nameInvalid");
    });
    if (enabled && rows.length === 0)
      found[LEVELS] = t("modalities.graduation.needLevel");
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    try {
      const saved = await save({
        enabled,
        requireOriginProof: proof,
        levels: rows.map((r) => ({
          ...(r.id ? { id: r.id } : {}),
          name: r.name.trim(),
          color: r.color,
          secondaryColor: r.secondaryColor || null,
          maxDegree: Number(r.maxDegree),
          minAge: num(r.minAge),
          maxAge: num(r.maxAge),
          kids: r.kids,
        })),
      });
      onSaved(saved);
    } catch (e) {
      const fields = e instanceof ApiError ? e.fields : [];
      const shown = /\.(name|maxDegree|minAge|maxAge)$|^levels$/;
      setErrors({
        ...Object.fromEntries(fields.map((f) => [f.field, f.message])),
        ...(fields.length === 0 || fields.some((f) => !shown.test(f.field))
          ? { form: t("modalities.graduation.saveError") }
          : {}),
      });
    } finally {
      setBusy(false);
    }
  }

  const err = (field: string, id: string) =>
    errors[field] ? (
      <p id={id} role="alert" className="mt-1 text-sm text-destructive">
        {errors[field]}
      </p>
    ) : null;

  return (
    <form
      className="flex flex-col gap-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <label className="flex min-h-11 items-center gap-2">
        <input
          type="checkbox"
          role="switch"
          checked={enabled}
          onChange={(e) => {
            setErrors({});
            setEnabled(e.target.checked);
          }}
        />
        {t("modalities.graduation.enabled")}
      </label>
      <label className="flex min-h-11 items-center gap-2">
        <input
          type="checkbox"
          checked={proof}
          onChange={(e) => setProof(e.target.checked)}
        />
        {t("modalities.graduation.requireProof")}
      </label>
      {err(LEVELS, LEVELS_ERROR)}
      {rows.length === 0 && (
        <p className="text-muted-foreground">
          {t("modalities.graduation.empty")}
        </p>
      )}
      <ol className="flex flex-col gap-3">
        {rows.map((r, i) => {
          const n = i + 1;
          const nameField = `levels[${i}].name`;
          return (
            <li
              key={r.key}
              className="flex flex-col gap-2 rounded-md border p-3"
            >
              <div>
                <label
                  htmlFor={`level-name-${r.key}`}
                  className="block text-sm font-medium"
                >
                  {t("modalities.graduation.levelName", { n })}
                </label>
                <input
                  id={`level-name-${r.key}`}
                  className={input}
                  value={r.name}
                  autoComplete="off"
                  aria-invalid={errors[nameField] ? true : undefined}
                  aria-describedby={
                    errors[nameField] ? `level-name-error-${r.key}` : undefined
                  }
                  onChange={(e) => patch(i, { name: e.target.value })}
                />
                {err(nameField, `level-name-error-${r.key}`)}
              </div>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <div>
                  <label
                    htmlFor={`level-color-${r.key}`}
                    className="block text-sm font-medium"
                  >
                    {t("modalities.graduation.color", { n })}
                  </label>
                  <input
                    id={`level-color-${r.key}`}
                    type="color"
                    className="min-h-11 w-full rounded-md border bg-background"
                    value={r.color}
                    onChange={(e) => patch(i, { color: e.target.value })}
                  />
                </div>
                <div>
                  <label
                    htmlFor={`level-degree-${r.key}`}
                    className="block text-sm font-medium"
                  >
                    {t("modalities.graduation.maxDegree", { n })}
                  </label>
                  <input
                    id={`level-degree-${r.key}`}
                    type="number"
                    min={0}
                    max={10}
                    className={input}
                    value={r.maxDegree}
                    onChange={(e) => patch(i, { maxDegree: e.target.value })}
                  />
                  {err(`levels[${i}].maxDegree`, `level-degree-error-${r.key}`)}
                </div>
                <div>
                  <label
                    htmlFor={`level-min-${r.key}`}
                    className="block text-sm font-medium"
                  >
                    {t("modalities.graduation.minAge", { n })}
                  </label>
                  <input
                    id={`level-min-${r.key}`}
                    type="number"
                    min={0}
                    max={120}
                    className={input}
                    value={r.minAge}
                    onChange={(e) => patch(i, { minAge: e.target.value })}
                  />
                  {err(`levels[${i}].minAge`, `level-min-error-${r.key}`)}
                </div>
                <div>
                  <label
                    htmlFor={`level-max-${r.key}`}
                    className="block text-sm font-medium"
                  >
                    {t("modalities.graduation.maxAge", { n })}
                  </label>
                  <input
                    id={`level-max-${r.key}`}
                    type="number"
                    min={0}
                    max={120}
                    className={input}
                    value={r.maxAge}
                    onChange={(e) => patch(i, { maxAge: e.target.value })}
                  />
                  {err(`levels[${i}].maxAge`, `level-max-error-${r.key}`)}
                </div>
              </div>
              <label className="flex min-h-11 items-center gap-2">
                <input
                  type="checkbox"
                  checked={r.kids}
                  onChange={(e) => patch(i, { kids: e.target.checked })}
                />
                {t("modalities.graduation.kids", { n })}
              </label>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={i === 0}
                  onClick={() => move(i, -1)}
                >
                  {t("modalities.graduation.up", { n })}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={i === rows.length - 1}
                  onClick={() => move(i, 1)}
                >
                  {t("modalities.graduation.down", { n })}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setErrors({});
                    setRows((all) => all.filter((_, j) => j !== i));
                  }}
                >
                  {t("modalities.graduation.remove", { n })}
                </Button>
              </div>
            </li>
          );
        })}
      </ol>
      <Button
        type="button"
        variant="outline"
        className="self-start"
        disabled={rows.length >= MAX_LEVELS}
        onClick={() =>
          setRows((all) => [
            ...all,
            {
              key: crypto.randomUUID(),
              name: "",
              color: "#808080",
              secondaryColor: "",
              maxDegree: "4",
              minAge: "",
              maxAge: "",
              kids: false,
            },
          ])
        }
      >
        {t("modalities.graduation.add")}
      </Button>
      {errors.form && (
        <p role="alert" className="text-sm text-destructive">
          {errors.form}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" onClick={onClose}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={busy}>
          {busy
            ? t("modalities.graduation.saving")
            : t("modalities.graduation.save")}
        </Button>
      </div>
    </form>
  );
}

// Carrega ao abrir: fechar desmonta, então reabrir sempre lê o estado atual do servidor.
function Body({
  modality,
  onClose,
  onSaved,
}: {
  modality: { id: string; name: string };
  onClose: () => void;
  onSaved: (modalityId: string, g: Graduation) => void;
}) {
  const g = useGraduation(modality.id);
  return (
    <>
      <p className="font-medium">{modality.name}</p>
      {g.status === "loading" && <LoadingState />}
      {g.status === "forbidden" && <ForbiddenState />}
      {g.status === "error" && <ErrorState onRetry={g.retry} />}
      {g.data &&
        (g.data.canManage ? (
          <Editor
            data={g.data}
            save={g.save}
            onClose={onClose}
            onSaved={(saved) => onSaved(modality.id, saved)}
          />
        ) : (
          <ReadOnly data={g.data} />
        ))}
    </>
  );
}

// RF-MOD-02: janela "Sistema de graduação". Coordenador edita (canManage); Administrador só consulta.
export function GraduationDialog({
  modality,
  onClose,
  onSaved,
}: {
  modality: { id: string; name: string } | null;
  onClose: () => void;
  onSaved: (modalityId: string, g: Graduation) => void;
}) {
  const { t } = useTranslation();
  return (
    <Modal
      open={modality !== null}
      onOpenChange={(o) => !o && onClose()}
      title={t("modalities.graduation.title")}
      className="max-h-[90vh] overflow-y-auto"
    >
      {modality && (
        <Body modality={modality} onClose={onClose} onSaved={onSaved} />
      )}
    </Modal>
  );
}
