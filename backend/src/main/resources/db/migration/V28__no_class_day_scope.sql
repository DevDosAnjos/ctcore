-- RF-AGE-05, RN-AGE-09/10: who launched the day (origin), the notice choice (nothing is sent before Sprint 09), the classes it affects (no rows =
-- every class of the unit) and which lessons a day cancelled, so undoing it never reopens a manual cancellation.
-- Revert (manual, no data loss outside these objects): drop index lesson_no_class_day; alter table lesson drop column no_class_day_id;
-- drop table no_class_day_class; alter table no_class_day drop column created_by, drop column notice_at, drop column notice_mode, drop constraint no_class_day_origin;
update no_class_day set origin = 'UNIT' where origin = 'MANUAL';
alter table no_class_day alter column origin set default 'UNIT';
alter table no_class_day add constraint no_class_day_origin check (origin in ('HOLIDAY', 'COORDINATOR', 'UNIT'));
alter table no_class_day add column notice_mode text not null default 'NONE' check (notice_mode in ('NONE', 'NOW', 'SCHEDULED'));
alter table no_class_day add column notice_at timestamptz;
alter table no_class_day add column created_by uuid;
alter table no_class_day add unique (organization_id, id);

create table no_class_day_class (
    organization_id uuid not null references organization(id),
    no_class_day_id uuid not null,
    class_id uuid not null,
    primary key (no_class_day_id, class_id),
    foreign key (organization_id, no_class_day_id) references no_class_day(organization_id, id) on delete cascade,
    foreign key (organization_id, class_id) references training_class(organization_id, id)
);
alter table no_class_day_class enable row level security;
alter table no_class_day_class force row level security;
create policy organization_scope on no_class_day_class
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table lesson add column no_class_day_id uuid;
alter table lesson add foreign key (organization_id, no_class_day_id) references no_class_day(organization_id, id) on delete set null (no_class_day_id);
create index lesson_no_class_day on lesson (no_class_day_id) where no_class_day_id is not null;
