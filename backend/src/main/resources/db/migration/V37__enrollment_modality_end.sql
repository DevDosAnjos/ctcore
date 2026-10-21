-- RN-MAT-17: reason and free-text note of a modality end. end_reason stays nullable for rows ended before this migration.
-- Revert (manual, loses only the reason and note): alter table enrollment_modality drop column end_note, drop column end_reason;
alter table enrollment_modality
    add column end_reason text check (end_reason is null or end_reason in ('CITY_CHANGE', 'FINANCIAL', 'SCHEDULE', 'INJURY', 'DISSATISFACTION', 'OTHER')),
    add column end_note text;
