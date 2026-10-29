import { useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ModalityDialog } from "@/features/students/ModalityPickers";
import { approveRequest, refuseRequest } from "./usePortalRequests";
import { requestSummary } from "./RequestSummary";
import type { PortalRequest } from "./types";

type Props = { request: PortalRequest; onClose: () => void; onDone: (decided: PortalRequest) => void };

function DecideDialog({ request, onClose, onDone, mode }: Props & { mode: "approve" | "refuse" }) {
  const { t } = useTranslation();
  const k = `portalRequests.${mode}.`;
  const id = useId();
  const [message, setMessage] = useState("");
  const decided = useRef<PortalRequest>(null);
  const trimmed = message.trim();
  return (
    <ModalityDialog
      title={t(`${k}title`)}
      effect={t(`${k}effect`)}
      state="ready"
      submitLabel={t(`${k}submit`)}
      canSubmit={mode === "approve" || trimmed.length >= 3}
      work={async () => {
        decided.current = await (mode === "approve" ? approveRequest(request.id, trimmed) : refuseRequest(request.id, trimmed));
      }}
      onClose={onClose}
      onDone={() => onDone(decided.current!)}
      errorPrefix="portalRequests.errors."
    >
      {(errors) => (
        <>
          <p className="text-sm">{t("portalRequests.decide.who", { student: request.studentName, summary: requestSummary(t, request) })}</p>
          {request.note && <p className="break-words text-sm text-muted-foreground">{t("portalRequests.decide.note", { note: request.note })}</p>}
          <div>
            <label htmlFor={id} className="block text-sm font-medium">
              {t(`${k}message`)}
            </label>
            <textarea
              id={id}
              className="w-full rounded-md border bg-background p-2"
              rows={3}
              maxLength={500}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              aria-invalid={errors.message ? true : undefined}
              aria-describedby={errors.message ? `${id}-erro` : undefined}
            />
            {errors.message && (
              <p id={`${id}-erro`} className="text-sm text-destructive">
                {errors.message}
              </p>
            )}
          </div>
        </>
      )}
    </ModalityDialog>
  );
}

export const RefuseRequestDialog = (p: Props) => <DecideDialog {...p} mode="refuse" />;
export const ApproveRequestDialog = (p: Props) => <DecideDialog {...p} mode="approve" />;
