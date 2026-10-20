import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router";
import { CpfField, PhoneField } from "@/components/fields/fields";
import { formatCep, isValidCpf, isValidPhone, onlyDigits } from "@/components/fields/masks";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { LogoCropDialog } from "@/features/organization/LogoCropDialog";
import { checkLogoFile, LOGO_TYPES } from "@/features/organization/logoFile";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { removeStudentPhoto, updateStudent, uploadStudentPhoto, useStudentDetail } from "./useStudentRecord";
import type { StudentDetail, StudentRef, StudentUpdate } from "./types";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const STATES = ["AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"];
const GENDERS = ["MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY"];
const REFERRALS = ["INSTAGRAM", "REFERRAL", "DIRECTORY", "WALK_BY", "OTHER"];
const FIELDS = ["name", "birthDate", "cpf", "email", "phone", "postalCode", "street", "number", "district", "city", "state", "emergencyName", "emergencyRelationship", "emergencyPhone", "form"] as const;
type Field = (typeof FIELDS)[number];
type Errors = Partial<Record<Field, string>>;
type Existing = { kind: "cpf" | "name"; ref: StudentRef };

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

const isAdult = (birth: string) => {
  const limit = new Date(`${birth}T00:00:00`);
  limit.setFullYear(limit.getFullYear() + 18);
  return limit.getTime() <= Date.now();
};

// RF-ALU-04: edição dos dados pessoais do aluno. O servidor reconfere tudo; aqui só se evita a ida e volta e se mostra o que ele recusar.
export function StudentEditDialog({ studentId, open, onClose, onSaved }: { studentId: string; open: boolean; onClose: () => void; onSaved: (saved: StudentDetail) => void }) {
  const { t } = useTranslation();
  const loaded = useStudentDetail(studentId);
  if (!open) return null;
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t("students.edit.title")}>
      {loaded.status === "loading" && <LoadingState />}
      {loaded.status === "forbidden" && <ForbiddenState />}
      {(loaded.status === "error" || loaded.status === "notFound") && <ErrorState onRetry={loaded.reload} />}
      {loaded.status === "ready" && loaded.data && <Form student={loaded.data} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function Form({ student, onClose, onSaved }: { student: StudentDetail; onClose: () => void; onSaved: (saved: StudentDetail) => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { search } = useLocation();
  const fileInput = useRef<HTMLInputElement>(null);
  const [name, setName] = useState(student.name);
  const [birthDate, setBirthDate] = useState(student.birthDate);
  const [cpf, setCpf] = useState(student.cpf ?? "");
  const [email, setEmail] = useState(student.email ?? "");
  const [phone, setPhone] = useState(student.phone ?? "");
  const [postalCode, setPostalCode] = useState(formatCep(student.postalCode));
  const [street, setStreet] = useState(student.street);
  const [number, setNumber] = useState(student.number);
  const [complement, setComplement] = useState(student.complement ?? "");
  const [district, setDistrict] = useState(student.district);
  const [city, setCity] = useState(student.city);
  const [state, setState] = useState(student.state);
  const [gender, setGender] = useState(student.gender ?? "");
  const [referral, setReferral] = useState(student.referral ?? "");
  const [emergencyName, setEmergencyName] = useState(student.emergencyName ?? "");
  const [emergencyRelationship, setEmergencyRelationship] = useState(student.emergencyRelationship ?? "");
  const [emergencyPhone, setEmergencyPhone] = useState(student.emergencyPhone ?? "");
  const [cropping, setCropping] = useState<File | null>(null);
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [removePhoto, setRemovePhoto] = useState(false);
  const [photoNotice, setPhotoNotice] = useState<string>();
  const [errors, setErrors] = useState<Errors>({});
  const [existing, setExisting] = useState<Existing>();
  const [busy, setBusy] = useState(false);
  // Dados já salvos, mas foto pendente: o envio da foto pode ser repetido sem salvar de novo (D6).
  const [saved, setSaved] = useState<StudentDetail>();
  const [photoFailed, setPhotoFailed] = useState(false);

  function validate(): Errors {
    const found: Errors = {};
    const required = t("students.edit.required");
    const n = name.trim();
    if (n.length < 2 || n.length > 80) found.name = t("students.edit.nameInvalid");
    if (!birthDate || birthDate >= new Date().toISOString().slice(0, 10)) found.birthDate = t("students.edit.birthDateInvalid");
    const adult = !!birthDate && isAdult(birthDate);
    if (cpf ? !isValidCpf(cpf) : adult) found.cpf = cpf ? t("students.edit.cpfInvalid") : required;
    if (email.trim() ? !EMAIL.test(email.trim()) : adult) found.email = email.trim() ? t("students.edit.emailInvalid") : required;
    if (phone ? !isValidPhone(phone) : adult) found.phone = phone ? t("students.edit.phoneInvalid") : required;
    if (onlyDigits(postalCode).length !== 8) found.postalCode = t("students.edit.postalCodeInvalid");
    if (!street.trim()) found.street = required;
    if (!number.trim()) found.number = required;
    if (!district.trim()) found.district = required;
    if (!city.trim()) found.city = required;
    if (!STATES.includes(state)) found.state = required;
    const given = [emergencyName.trim(), emergencyRelationship.trim(), emergencyPhone].filter(Boolean).length;
    if (given > 0 && given < 3) {
      if (!emergencyName.trim()) found.emergencyName = t("students.edit.emergencyAll");
      else if (!emergencyRelationship.trim()) found.emergencyRelationship = t("students.edit.emergencyAll");
      else found.emergencyPhone = t("students.edit.emergencyAll");
    }
    return found;
  }

  const input = (confirmDuplicate: boolean): StudentUpdate => ({
    name: name.trim(),
    birthDate,
    postalCode: onlyDigits(postalCode),
    street: street.trim(),
    number: number.trim(),
    complement: complement.trim() || null,
    district: district.trim(),
    city: city.trim(),
    state,
    cpf: cpf || null,
    email: email.trim() || null,
    phone: phone || null,
    gender: gender || null,
    referral: referral || null,
    emergencyName: emergencyName.trim() || null,
    emergencyRelationship: emergencyRelationship.trim() || null,
    emergencyPhone: emergencyPhone || null,
    ...(confirmDuplicate ? { confirmDuplicate: true } : {}),
  });

  function pickFile(file: File | undefined) {
    if (!file) return;
    const problem = checkLogoFile(file);
    if (problem) return setPhotoNotice(t(problem === "type" ? "students.edit.photoTypeError" : "students.edit.photoSizeError"));
    setPhotoNotice(undefined);
    setCropping(file);
  }

  // Depois dos dados salvos: envia ou remove a foto. Falha mantém os dados e deixa tentar de novo.
  async function finish(current: StudentDetail) {
    let latest = current;
    try {
      if (photo) latest = await uploadStudentPhoto(current.id, photo);
      else if (removePhoto && current.photoUrl) latest = await removeStudentPhoto(current.id);
    } catch {
      setPhotoFailed(true);
      return;
    }
    onClose();
    onSaved(latest);
  }

  async function submit(confirmDuplicate = false) {
    if (!saved) {
      const found = validate();
      setErrors(found);
      setExisting(undefined);
      if (Object.keys(found).length) return;
    }
    setBusy(true);
    try {
      let current = saved;
      if (!current) {
        current = await updateStudent(student.id, input(confirmDuplicate));
        setSaved(current);
      }
      await finish(current);
    } catch (e) {
      const next: Errors = {};
      const ref = e instanceof ApiError && e.fields.length >= 2 ? { id: e.fields[0].message, name: e.fields[1].message, photoUrl: e.fields[2]?.message || null } : null;
      if (e instanceof ApiError && ref && (e.code === "STUDENT_CPF_EXISTS" || e.code === "STUDENT_POSSIBLE_DUPLICATE")) {
        setExisting({ kind: e.code === "STUDENT_CPF_EXISTS" ? "cpf" : "name", ref });
      } else if (e instanceof ApiError && e.status === 403) next.form = t("states.forbiddenText");
      else if (e instanceof ApiError && e.fields.length) {
        for (const f of e.fields) next[(FIELDS as readonly string[]).includes(f.field) ? (f.field as Field) : "form"] = f.message;
      } else next.form = t("students.edit.error");
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }

  const showsPhoto = !removePhoto && !photo && student.photoUrl;
  const hasPhoto = !!photo || !!showsPhoto;
  const k = "students.edit.";

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
              <img src={student.photoUrl ?? ""} alt="" className="size-16 shrink-0 rounded-full object-cover" />
            ) : (
              <span aria-hidden="true" className="flex size-16 shrink-0 items-center justify-center rounded-full bg-muted text-xl font-semibold">
                {initials(name || student.name)}
              </span>
            )}
            <div className="flex min-w-0 flex-col gap-1">
              <span className="text-sm font-medium">{t(`${k}photo`)}</span>
              <p className="text-sm text-muted-foreground">{photo ? t(`${k}photoChosen`) : t(`${k}photoHint`)}</p>
              <input
                ref={fileInput}
                type="file"
                hidden
                aria-label={t(`${k}photoFile`)}
                accept={LOGO_TYPES.join(",")}
                onChange={(e) => {
                  pickFile(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" className="min-h-11" onClick={() => fileInput.current?.click()}>
                  {hasPhoto ? t(`${k}photoReplace`) : t(`${k}photoChoose`)}
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
                    {t(`${k}photoRemove`)}
                  </Button>
                )}
              </div>
              {photoNotice && (
                <p role="alert" className="text-sm text-destructive">
                  {photoNotice}
                </p>
              )}
            </div>
          </div>
          <Text label={t(`${k}name`)} value={name} onChange={setName} error={errors.name} autoComplete="off" />
          <Text label={t(`${k}birthDate`)} type="date" value={birthDate} onChange={setBirthDate} error={errors.birthDate} />
          <div>
            <CpfField label={t(`${k}cpf`)} initial={cpf} onChange={setCpf} autoComplete="off" />
            {errors.cpf && <p className="text-sm text-destructive">{errors.cpf}</p>}
          </div>
          <Text label={t(`${k}email`)} type="email" value={email} onChange={setEmail} error={errors.email} autoComplete="off" />
          <div>
            <PhoneField label={t(`${k}phone`)} initial={phone} onChange={setPhone} autoComplete="off" />
            {errors.phone && <p className="text-sm text-destructive">{errors.phone}</p>}
          </div>
          <fieldset className="flex flex-col gap-3">
            <legend className="text-sm font-medium">{t(`${k}address`)}</legend>
            <Text label={t(`${k}postalCode`)} value={postalCode} onChange={(v) => setPostalCode(formatCep(v))} error={errors.postalCode} inputMode="numeric" autoComplete="off" />
            <Text label={t(`${k}street`)} value={street} onChange={setStreet} error={errors.street} autoComplete="off" />
            <Text label={t(`${k}number`)} value={number} onChange={setNumber} error={errors.number} autoComplete="off" />
            <Text label={t(`${k}complement`)} value={complement} onChange={setComplement} autoComplete="off" />
            <Text label={t(`${k}district`)} value={district} onChange={setDistrict} error={errors.district} autoComplete="off" />
            <Text label={t(`${k}city`)} value={city} onChange={setCity} error={errors.city} autoComplete="off" />
            <label className="block text-sm font-medium">
              {t(`${k}state`)}
              <select
                className="mt-1 min-h-11 w-full rounded-md border bg-background px-2 font-normal"
                value={state}
                aria-invalid={errors.state ? true : undefined}
                onChange={(e) => setState(e.target.value)}
              >
                <option value="">{t(`${k}stateChoose`)}</option>
                {STATES.map((uf) => (
                  <option key={uf} value={uf}>
                    {uf}
                  </option>
                ))}
              </select>
            </label>
            {errors.state && <p className="text-sm text-destructive">{errors.state}</p>}
          </fieldset>
          <label className="block text-sm font-medium">
            {t(`${k}gender`)}
            <select className="mt-1 min-h-11 w-full rounded-md border bg-background px-2 font-normal" value={gender} onChange={(e) => setGender(e.target.value)}>
              <option value="">{t(`${k}none`)}</option>
              {GENDERS.map((g) => (
                <option key={g} value={g}>
                  {t(`${k}genders.${g}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm font-medium">
            {t(`${k}referral`)}
            <select className="mt-1 min-h-11 w-full rounded-md border bg-background px-2 font-normal" value={referral} onChange={(e) => setReferral(e.target.value)}>
              <option value="">{t(`${k}none`)}</option>
              {REFERRALS.map((r) => (
                <option key={r} value={r}>
                  {t(`${k}referrals.${r}`)}
                </option>
              ))}
            </select>
          </label>
          <fieldset className="flex flex-col gap-3">
            <legend className="text-sm font-medium">{t(`${k}emergency`)}</legend>
            <Text label={t(`${k}emergencyName`)} value={emergencyName} onChange={setEmergencyName} error={errors.emergencyName} autoComplete="off" />
            <Text label={t(`${k}emergencyRelationship`)} value={emergencyRelationship} onChange={setEmergencyRelationship} error={errors.emergencyRelationship} autoComplete="off" />
            <div>
              <PhoneField label={t(`${k}emergencyPhone`)} initial={emergencyPhone} onChange={setEmergencyPhone} autoComplete="off" />
              {errors.emergencyPhone && <p className="text-sm text-destructive">{errors.emergencyPhone}</p>}
            </div>
          </fieldset>
        </fieldset>
        {existing && (
          <div role="alert" className="flex flex-col gap-2 rounded-md border p-3">
            <div className="flex items-center gap-2">
              {existing.ref.photoUrl ? (
                <img src={existing.ref.photoUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
              ) : (
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold">
                  {initials(existing.ref.name)}
                </span>
              )}
              <p className="min-w-0 break-words text-sm">{t(existing.kind === "cpf" ? `${k}cpfExists` : `${k}possibleDuplicate`, { name: existing.ref.name })}</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                className="min-h-11"
                onClick={() => {
                  onClose();
                  navigate(`/alunos/${existing.ref.id}${search}`);
                }}
              >
                {t(`${k}openRecord`)}
              </Button>
              {existing.kind === "name" && (
                <Button type="button" className="min-h-11" disabled={busy} onClick={() => void submit(true)}>
                  {t(`${k}confirmDuplicate`)}
                </Button>
              )}
            </div>
          </div>
        )}
        {errors.form && (
          <p role="alert" className="text-sm text-destructive">
            {errors.form}
          </p>
        )}
        {photoFailed && (
          <p role="alert" className="text-sm text-destructive">
            {t(`${k}photoUploadError`)}
          </p>
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
              {t(`${k}closeWithoutPhoto`)}
            </Button>
          ) : (
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
              {t("common.cancel")}
            </Button>
          )}
          <Button type="submit" className="min-h-11" disabled={busy}>
            {saved && photoFailed ? t(`${k}photoRetry`) : t(`${k}save`)}
          </Button>
        </div>
      </form>
      {cropping && (
        <LogoCropDialog
          file={cropping}
          labels={{ title: t(`${k}cropTitle`), hint: t(`${k}cropHint`), preview: t(`${k}cropPreview`), error: t(`${k}photoTypeError`) }}
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
