-- RN-PRO-08: marks a benefit as a teacher discount (an ordinary benefit tied to the teacher), so deactivating the teacher can end it.
-- Revert (manual, loses only the mark): drop index benefit_teacher_active; alter table benefit drop column teacher_id;
alter table benefit add column teacher_id uuid;
alter table benefit add constraint benefit_teacher_fk foreign key (organization_id, teacher_id) references teacher(organization_id, id);
create index benefit_teacher_active on benefit (teacher_id) where status = 'ACTIVE';
