import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CharCounter } from "@/components/CharCounter";
import { PhoneField } from "@/components/fields/fields";
import { Modal } from "@/components/Modal";
import { ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { LogoCropDialog } from "@/features/organization/LogoCropDialog";
import { checkLogoFile, LOGO_TYPES } from "@/features/organization/logoFile";
import { Text } from "@/features/units/Text";
import { useUnitScope } from "@/features/units/UnitScopeProvider";
import { UnitChecks } from "@/features/users/UnitChecks";
import { ApiError } from "@/lib/api";
import {
  createTeacher,
  removeTeacherPhoto,
  updateTeacher,
  uploadTeacherPhoto,
  useTeacherOptions,
  type TeacherDetail,
  type TeacherInput,
  type TeacherOptions,
} from "./useTeachers";
import { Select, Input } from "@/components/ui/field";
import { Banner } from "@/components/ui/banner";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const BIO_MAX = 300;
type Errors = Partial<Record<"name" | "email" | "phone" | "bio" | "units" | "modalities" | "declared" | "form", string>>;
type Picked = Record<string, { levelId: string; degree: string }>;
// Campo do servidor -> campo da tela.
const FIELD: Record<string, keyof Errors> = { name: "name", email: "email", phone: "phone", bio: "bio", unitIds: "units", modalities: "modalities", consentDeclaredByManagement: "declared" };

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

// RF-PRO-03: cadastro e edição do professor. Sem `teacher` cria; com ele edita. O servidor reconfere tudo (inclusive alcance).
export function TeacherFormDialog({ open, teacher, onClose, onSaved }: { open: boolean; teacher?: TeacherDetail; onClose: () => void; onSaved: (saved: TeacherDetail) => void }) {
  const { t } = useTranslation();
  const loaded = useTeacherOptions(open);
  if (!open) return null;
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t(teacher ? "teachers.form.editTitle" : "teachers.form.newTitle")}>
      {loaded.status === "loading" && <LoadingState />}
      {loaded.status === "error" && <ErrorState onRetry={loaded.retry} />}
      {loaded.options && <Form key={teacher?.id ?? "new"} options={loaded.options} teacher={teacher} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function Form({ options, teacher, onClose, onSaved }: { options: TeacherOptions; teacher?: TeacherDetail; onClose: () => void; onSaved: (saved: TeacherDetail) => void }) {
  const { t } = useTranslation();
  const { units } = useUnitScope();
  const fileInput = useRef<HTMLInputElement>(null);
  const [name, setName] = useState(teacher?.name ?? "");
  const [email, setEmail] = useState(teacher?.email ?? "");
  const [phone, setPhone] = useState(teacher?.phone ?? "");
  const [bio, setBio] = useState(teacher?.bio ?? "");
  const [unitIds, setUnitIds] = useState(() => (teacher?.units ?? []).map((u) => u.id).filter((id) => units.some((r) => r.id === id)));
  const [picked, setPicked] = useState<Picked>(() =>
    Object.fromEntries(
      (teacher?.modalities ?? [])
        .filter((m) => options.modalities.some((o) => o.id === m.id))
        .map((m) => [m.id, { levelId: m.levelId ?? "", degree: String(m.degree ?? 0) }]),
    ),
  );
  const [consent, setConsent] = useState(teacher?.directoryConsent ?? false);
  const [declared, setDeclared] = useState(teacher?.consentDeclaredByManagement ?? false);
  const [cropping, setCropping] = useState<File | null>(null);
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [removePhoto, setRemovePhoto] = useState(false);
  const [photoNotice, setPhotoNotice] = useState<string>();
  const [errors, setErrors] = useState<Errors>({});
  const [blocking, setBlocking] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  // Cadastro já salvo, mas foto pendente: o envio da foto pode ser repetido sem salvar de novo (D1).
  const [saved, setSaved] = useState<TeacherDetail>();
  const [photoFailed, setPhotoFailed] = useState(false);

  function toggle(id: string, on: boolean) {
    setPicked((p) => {
      const { [id]: _drop, ...rest } = p; // eslint-disable-line @typescript-eslint/no-unused-vars
      return on ? { ...rest, [id]: { levelId: "", degree: "0" } } : rest;
    });
  }

  function pickFile(file: File | undefined) {
    if (!file) return;
    const problem = checkLogoFile(file);
    if (problem) return setPhotoNotice(t(problem === "type" ? "teachers.form.photoTypeError" : "teachers.form.photoSizeError"));
    setPhotoNotice(undefined);
    setCropping(file);
  }

  function validate(): Errors {
    const found: Errors = {};
    const n = name.trim();
    if (n.length < 2 || n.length > 80) found.name = t("teachers.form.nameInvalid");
    if (email.trim() && !EMAIL.test(email.trim())) found.email = t("teachers.form.emailInvalid");
    if (unitIds.length === 0) found.units = t("users.invite.unitsRequired");
    if (consent && !declared) found.declared = t("teachers.form.declaredRequired");
    if (options.modalities.some((o) => o.graduationEnabled && picked[o.id] && !picked[o.id].levelId)) found.modalities = t("teachers.form.levelRequired");
    return found;
  }

  function input(): TeacherInput {
    return {
      name: name.trim(),
      email: email.trim() || null,
      phone: phone || null,
      bio: bio.trim() || null,
      directoryConsent: consent,
      consentDeclaredByManagement: declared,
      unitIds,
      modalities: options.modalities
        .filter((o) => picked[o.id])
        .map((o) => (o.graduationEnabled ? { modalityId: o.id, levelId: picked[o.id].levelId, degree: Number(picked[o.id].degree) } : { modalityId: o.id, levelId: null, degree: null })),
    };
  }

  // Depois do cadastro salvo: envia ou remove a foto. Falha mantém o cadastro e deixa tentar de novo.
  async function finish(current: TeacherDetail) {
    let latest = current;
    try {
      if (photo) latest = await uploadTeacherPhoto(current.id, photo);
      else if (removePhoto && current.photoUrl) latest = await removeTeacherPhoto(current.id);
    } catch {
      setPhotoFailed(true);
      return;
    }
    onClose();
    onSaved(latest);
  }

  async function submit() {
    if (!saved) {
      const found = validate();
      setErrors(found);
      if (Object.keys(found).length) return;
    }
    setBusy(true);
    try {
      let current = saved;
      if (!current) {
        current = teacher ? await updateTeacher(teacher.id, input()) : await createTeacher(input());
        setSaved(current);
      }
      await finish(current);
    } catch (e) {
      const next: Errors = {};
      // RN-PRO-09: modalidade retirada com turma ativa (409): mostra as turmas que impedem.
      const inUse = e instanceof ApiError && e.code === "TEACHER_MODALITY_IN_USE";
      setBlocking(inUse ? e.fields.filter((f) => f.field === "class").map((f) => f.message) : []);
      if (inUse) next.modalities = t("teachers.form.modalityInUse");
      else if (e instanceof ApiError && e.fields.length) for (const f of e.fields) next[FIELD[f.field] ?? "form"] = f.message;
      else next.form = t("teachers.form.error");
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }

  const showsPhoto = !removePhoto && !photo && teacher?.photoUrl;
  const hasPhoto = !!photo || !!showsPhoto;

  return (
    <>
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy) void submit();
        }}
      >
        <fieldset disabled={!!saved} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-3">
            {showsPhoto ? (
              <img src={teacher?.photoUrl ?? ""} alt="" className="size-16 shrink-0 rounded-full object-cover" />
            ) : (
              <span aria-hidden="true" className="flex size-16 shrink-0 items-center justify-center rounded-full bg-muted text-xl font-semibold">
                {initials(name || teacher?.name || "")}
              </span>
            )}
            <div className="flex min-w-0 flex-col gap-1">
              <span className="text-sm font-medium">{t("teachers.form.photo")}</span>
              <p className="text-sm text-muted-foreground">{photo ? t("teachers.form.photoChosen") : t("teachers.form.photoHint")}</p>
              <Input
                ref={fileInput}
                type="file"
                hidden
                aria-label={t("teachers.form.photoFile")}
                accept={LOGO_TYPES.join(",")}
                onChange={(e) => {
                  pickFile(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" className="min-h-11" onClick={() => fileInput.current?.click()}>
                  {hasPhoto ? t("teachers.form.photoReplace") : t("teachers.form.photoChoose")}
                </Button>
                {hasPhoto && (
                  <Button
                    type="button"
                    variant="outline"
                    className="min-h-11"
                    onClick={() => {
                      setPhoto(null);
                      setRemovePhoto(true);
                    }}
                  >
                    {t("teachers.form.photoRemove")}
                  </Button>
                )}
              </div>
              {photoNotice && (
                <Banner tone="danger">
                  {photoNotice}
                </Banner>
              )}
            </div>
          </div>
          <Text label={t("teachers.form.name")} value={name} onChange={setName} error={errors.name} autoComplete="off" />
          <Text label={t("teachers.form.email")} type="email" value={email} onChange={setEmail} error={errors.email} autoComplete="off" />
          <div>
            <PhoneField label={t("teachers.form.phone")} initial={phone} onChange={setPhone} autoComplete="off" />
            {errors.phone && <p className="text-sm text-danger-foreground">{errors.phone}</p>}
          </div>
          <div>
            <label htmlFor="teacher-bio" className="block text-sm font-medium">
              {t("teachers.form.bio")}
            </label>
            <CharCounter id="teacher-bio" rows={3} value={bio} max={BIO_MAX} onChange={setBio} aria-invalid={errors.bio ? true : undefined} />
            {errors.bio && <p className="text-sm text-danger-foreground">{errors.bio}</p>}
          </div>
          <UnitChecks units={units} value={unitIds} onChange={setUnitIds} error={errors.units} />
          <fieldset aria-describedby={errors.modalities ? "teacher-modalities-erro" : undefined}>
            <legend className="text-sm font-medium">{t("teachers.form.modalities")}</legend>
            {options.modalities.length === 0 && <p className="text-sm text-muted-foreground">{t("teachers.form.noModalities")}</p>}
            {options.modalities.map((o) => {
              const sel = picked[o.id];
              const level = o.levels.find((l) => l.id === sel?.levelId);
              return (
                <div key={o.id} className="flex flex-col gap-1">
                  <label className="flex min-h-11 items-center gap-2">
                    <input type="checkbox" checked={!!sel} onChange={(e) => toggle(o.id, e.target.checked)} />
                    {o.name}
                  </label>
                  {sel && o.graduationEnabled && (
                    <div className="ml-6 flex flex-wrap gap-2">
                      <label className="flex flex-col text-sm">
                        {t("teachers.form.level", { name: o.name })}
                        <Select
                          className="min-h-11"
                          value={sel.levelId}
                          onChange={(e) => setPicked((p) => ({ ...p, [o.id]: { levelId: e.target.value, degree: "0" } }))}
                        >
                          <option value="">{t("teachers.form.levelChoose")}</option>
                          {o.levels.map((l) => (
                            <option key={l.id} value={l.id}>
                              {l.name}
                            </option>
                          ))}
                        </Select>
                      </label>
                      <label className="flex flex-col text-sm">
                        {t("teachers.form.degree", { name: o.name })}
                        <Select
                          className="min-h-11"
                          value={sel.degree}
                          disabled={!level}
                          onChange={(e) => setPicked((p) => ({ ...p, [o.id]: { ...p[o.id], degree: e.target.value } }))}
                        >
                          {Array.from({ length: (level?.maxDegree ?? 0) + 1 }, (_, d) => (
                            <option key={d} value={d}>
                              {d}
                            </option>
                          ))}
                        </Select>
                      </label>
                    </div>
                  )}
                </div>
              );
            })}
            {errors.modalities && (
              <p id="teacher-modalities-erro" className="text-sm text-danger-foreground">
                {errors.modalities}
              </p>
            )}
            {errors.modalities && blocking.length > 0 && (
              <ul className="list-disc pl-5 text-sm text-danger-foreground">
                {blocking.map((name) => (
                  <li key={name}>{name}</li>
                ))}
              </ul>
            )}
          </fieldset>
          <fieldset aria-describedby={errors.declared ? "teacher-declared-erro" : undefined}>
            <legend className="text-sm font-medium">{t("teachers.form.directory")}</legend>
            <label className="flex min-h-11 items-center gap-2">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
              {t("teachers.form.directoryConsent")}
            </label>
            <label className="flex min-h-11 items-center gap-2">
              <input type="checkbox" checked={declared} onChange={(e) => setDeclared(e.target.checked)} />
              {t("teachers.form.declared")}
            </label>
            {errors.declared && (
              <p id="teacher-declared-erro" className="text-sm text-danger-foreground">
                {errors.declared}
              </p>
            )}
          </fieldset>
        </fieldset>
        {errors.form && (
          <Banner tone="danger">
            {errors.form}
          </Banner>
        )}
        {photoFailed && (
          <Banner tone="danger">
            {t("teachers.form.photoUploadError")}
          </Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          {saved && photoFailed ? (
            <Button
              type="button"
              variant="outline"
              className="min-h-11"
              disabled={busy}
              onClick={() => {
                onClose();
                onSaved(saved);
              }}
            >
              {t("teachers.form.closeWithoutPhoto")}
            </Button>
          ) : (
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
              {t("common.cancel")}
            </Button>
          )}
          <Button type="submit" className="min-h-11" disabled={busy}>
            {saved && photoFailed ? t("teachers.form.photoRetry") : t("teachers.form.save")}
          </Button>
        </div>
      </form>
      {cropping && (
        <LogoCropDialog
          file={cropping}
          labels={{ title: t("teachers.form.cropTitle"), hint: t("teachers.form.cropHint"), preview: t("teachers.form.cropPreview"), error: t("teachers.form.photoTypeError") }}
          onCancel={() => setCropping(null)}
          onConfirm={async (blob) => {
            setPhoto(blob);
            setRemovePhoto(false);
            setCropping(null);
          }}
        />
      )}
    </>
  );
}
