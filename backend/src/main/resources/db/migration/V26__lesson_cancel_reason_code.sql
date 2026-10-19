-- RF-AGE-03, RN-AGE-04/05: the cancellation reason becomes a code (the screen translates it); cancel_reason keeps only the free text of OTHER
-- (or the text copied from a no-class day). original_* remember the generated slot of a lesson that was edited, so the daily generator
-- does not recreate it (RN-AGE-01).
-- Revert (manual, no data loss outside these columns): drop index lesson_original_slot; alter table lesson drop column original_starts_at,
-- drop column original_date, drop column cancel_reason_code;
alter table lesson add column cancel_reason_code text
    check (cancel_reason_code in ('TEACHER_UNAVAILABLE', 'HOLIDAY', 'ACADEMY_EVENT', 'OTHER'));
alter table lesson add column original_date date;
alter table lesson add column original_starts_at time;
create index lesson_original_slot on lesson (class_id, original_date, original_starts_at) where original_date is not null;
