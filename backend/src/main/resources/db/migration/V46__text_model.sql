-- RF-COM-03 (RN-COM-12, #83): texto próprio de um modelo de e-mail editável, um por organização e modelo. Sem linha, vale o texto da plataforma.
-- Diferente de terms_version, o texto é regravado e apagado ("Voltar ao texto da plataforma"); o antes e depois fica na auditoria.
-- Revert (manual): drop table text_model;
create table text_model (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    key text not null check (key in ('REMINDER_BEFORE', 'REMINDER_DUE', 'REMINDER_OVERDUE', 'WELCOME', 'ANNOUNCEMENT_CANCELED_CLASS',
        'ANNOUNCEMENT_TEACHER_CHANGE', 'ANNOUNCEMENT_SCHEDULE_CHANGE', 'ANNOUNCEMENT_NO_CLASS_DAY', 'ANNOUNCEMENT_GENERAL')),
    subject text not null check (char_length(subject) between 1 and 150),
    body text not null check (char_length(body) between 1 and 2000),
    updated_by_account_id uuid not null references account(id),
    updated_at timestamptz not null default now(),
    unique (organization_id, key)
);

alter table text_model enable row level security;
alter table text_model force row level security;
create policy organization_scope on text_model
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
