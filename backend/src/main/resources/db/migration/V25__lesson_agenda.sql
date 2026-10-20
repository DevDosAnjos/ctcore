-- RF-AGE-01, RN-AGE-01/03/08: dated lessons and the days a unit has no class. The status (scheduled, in progress, completed) is never stored: it is
-- computed on read from the unit clock; only the cancellation is data. No attendance or waiting-list data (RN-AGE-13).
-- Revert (manual, no data loss outside these objects): drop function active_organization_ids(); drop table no_class_day; drop table lesson;
create table lesson (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    class_id uuid,
    lesson_date date not null,
    starts_at time not null,
    ends_at time not null,
    teacher_id uuid not null,
    substitute boolean not null default false,
    cancelled boolean not null default false,
    cancel_reason text,
    staff_note text,
    manually_edited boolean not null default false,
    created_at timestamptz not null default now(),
    check (ends_at > starts_at),
    unique (organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, class_id) references training_class(organization_id, id),
    foreign key (organization_id, teacher_id) references teacher(organization_id, id)
);
-- RN-AGE-01: one lesson per class, date and start; the generator relies on it to be idempotent.
create unique index lesson_class_date_start on lesson (class_id, lesson_date, starts_at) where class_id is not null;
create index lesson_unit_date on lesson (organization_id, unit_id, lesson_date);

create table no_class_day (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    starts_on date not null,
    ends_on date not null,
    reason text not null,
    origin text not null default 'MANUAL',
    notice_status text not null default 'Ainda não avisado',
    created_at timestamptz not null default now(),
    check (ends_on >= starts_on),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
create index no_class_day_unit_start on no_class_day (organization_id, unit_id, starts_on);

alter table lesson enable row level security;
alter table lesson force row level security;
create policy organization_scope on lesson
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table no_class_day enable row level security;
alter table no_class_day force row level security;
create policy organization_scope on no_class_day
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- The daily job has no organization yet: this is the only way to list those with an active unit. It returns ids only and runs with the owner's
-- rights (the migration user, who bypasses RLS); the search path is fixed so it cannot be hijacked.
create function active_organization_ids() returns setof uuid
language sql stable security definer set search_path = public, pg_temp
as $$ select distinct organization_id from unit where active $$;
