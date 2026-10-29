import { useState } from "react";
import { Select } from "@/components/ui/field";
import { useTranslation } from "react-i18next";
import { PhoneField } from "@/components/fields/fields";
import { formatCep, isValidPhone, onlyDigits } from "@/components/fields/masks";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { savePortalContact, type InfoPersonal } from "./usePortalInfo";

const STATES = ["AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"];
type Errors = Record<string, string>;

// RN-ALU-11, RN-ALU-03: só telefone, endereço e contato de emergência; as mesmas regras do cadastro, conferidas de novo no servidor (422 por campo).
export function PortalContactDialog({ studentId, personal, onClose, onSaved }: { studentId: string; personal: InfoPersonal; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const k = "portalInfo.contact.";
  const { address: a, emergency: e } = personal;
  const [phone, setPhone] = useState(onlyDigits(personal.phone ?? ""));
  const [postalCode, setPostalCode] = useState(formatCep(a.postalCode));
  const [street, setStreet] = useState(a.street);
  const [number, setNumber] = useState(a.number);
  const [complement, setComplement] = useState(a.complement ?? "");
  const [district, setDistrict] = useState(a.district);
  const [city, setCity] = useState(a.city);
  const [state, setState] = useState(a.state);
  const [emergencyName, setEmergencyName] = useState(e.name ?? "");
  const [emergencyRelationship, setEmergencyRelationship] = useState(e.relationship ?? "");
  const [emergencyPhone, setEmergencyPhone] = useState(onlyDigits(e.phone ?? ""));
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);

  async function submit() {
    const found: Errors = {};
    const need = (field: string, value: string) => !value.trim() && (found[field] = t(`${k}required`));
    need("street", street);
    need("number", number);
    need("district", district);
    need("city", city);
    need("state", state);
    if (onlyDigits(postalCode).length !== 8) found.postalCode = t(`${k}postalCodeInvalid`);
    if (phone && !isValidPhone(phone)) found.phone = t("fields.phoneInvalid");
    if (emergencyPhone && !isValidPhone(emergencyPhone)) found.emergencyPhone = t("fields.phoneInvalid");
    const filled = [emergencyName.trim(), emergencyRelationship.trim(), emergencyPhone].filter(Boolean).length;
    if (filled > 0 && filled < 3) found.emergencyName = t(`${k}emergencyAll`);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    try {
      await savePortalContact(studentId, {
        phone: phone || null,
        postalCode: onlyDigits(postalCode),
        street: street.trim(),
        number: number.trim(),
        complement: complement.trim() || null,
        district: district.trim(),
        city: city.trim(),
        state,
        emergencyName: emergencyName.trim() || null,
        emergencyRelationship: emergencyRelationship.trim() || null,
        emergencyPhone: emergencyPhone || null,
      });
      onSaved();
      onClose();
    } catch (err) {
      const fields = err instanceof ApiError && err.status === 422 ? err.fields : [];
      if (fields.length > 0) setErrors(Object.fromEntries(fields.map((f) => [f.field, t(`${k}invalid`)])));
      else setErrors({ form: t(`${k}failed`) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(open) => !open && !busy && onClose()} title={t(`${k}title`)} className="max-h-[90vh] overflow-y-auto">
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(ev) => {
          ev.preventDefault();
          if (!busy) void submit();
        }}
      >
        {errors.form && (
          <p role="alert" className="text-sm text-danger-foreground">
            {errors.form}
          </p>
        )}
        <div>
          <PhoneField label={t(`${k}phone`)} initial={phone} onChange={setPhone} autoComplete="tel" />
          {errors.phone && (
            <p role="alert" className="text-sm text-danger-foreground">
              {errors.phone}
            </p>
          )}
        </div>
        <Text label={t(`${k}postalCode`)} value={postalCode} onChange={(v) => setPostalCode(formatCep(v))} error={errors.postalCode} inputMode="numeric" autoComplete="postal-code" />
        <Text label={t(`${k}street`)} value={street} onChange={setStreet} error={errors.street} autoComplete="address-line1" />
        <Text label={t(`${k}number`)} value={number} onChange={setNumber} error={errors.number} />
        <Text label={t(`${k}complement`)} value={complement} onChange={setComplement} error={errors.complement} />
        <Text label={t(`${k}district`)} value={district} onChange={setDistrict} error={errors.district} />
        <Text label={t(`${k}city`)} value={city} onChange={setCity} error={errors.city} />
        <div>
          <label htmlFor="pc-state" className="block text-sm font-medium">
            {t(`${k}state`)}
          </label>
          <Select id="pc-state" value={state} aria-invalid={!!errors.state || undefined} onChange={(ev) => setState(ev.target.value)}>
            <option value="">{t(`${k}stateChoose`)}</option>
            {STATES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
          {errors.state && <p className="text-sm text-danger-foreground">{errors.state}</p>}
        </div>
        <fieldset className="flex flex-col gap-3">
          <legend className="text-sm font-semibold">{t(`${k}emergency`)}</legend>
          <Text label={t(`${k}emergencyName`)} value={emergencyName} onChange={setEmergencyName} error={errors.emergencyName} />
          <Text label={t(`${k}emergencyRelationship`)} value={emergencyRelationship} onChange={setEmergencyRelationship} error={errors.emergencyRelationship} />
          <div>
            <PhoneField label={t(`${k}emergencyPhone`)} initial={emergencyPhone} onChange={setEmergencyPhone} />
            {errors.emergencyPhone && (
              <p role="alert" className="text-sm text-danger-foreground">
                {errors.emergencyPhone}
              </p>
            )}
          </div>
        </fieldset>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" disabled={busy} onClick={onClose}>
            {t(`${k}cancel`)}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {busy ? t(`${k}saving`) : t(`${k}save`)}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
