-- RF-AGE-04, RN-AGE-12: a one-off lesson has no class (class_id null); it keeps its own title, modality and, optionally, the class that may see it
-- (null = every class of the unit). A lesson of a class keeps all three null.
-- Revert (manual, no data loss outside these columns): alter table lesson drop constraint lesson_adhoc_shape, drop column visibility_class_id,
-- drop column modality_id, drop column title;
alter table lesson add column title text check (char_length(btrim(title)) between 1 and 80);
alter table lesson add column modality_id uuid references modality(id);
alter table lesson add column visibility_class_id uuid;
alter table lesson add foreign key (organization_id, visibility_class_id) references training_class(organization_id, id);
alter table lesson add constraint lesson_adhoc_shape check (
    (class_id is null and title is not null and modality_id is not null)
    or (class_id is not null and title is null and modality_id is null and visibility_class_id is null));
