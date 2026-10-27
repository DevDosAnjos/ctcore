-- RF-POR-06 (#93): "Marcar todos como lidos" no portal. announcement_dismissal guarda, por conta e comunicado, que a conta limpou o aviso; nao e leitura (RN-COM-08):
-- a contagem de leitores da gestao continua em announcement_read. Sem UPDATE nem DELETE para o papel operacional; o comunicado agendado apagado leva a marca junto.
-- Revert (manual): drop table announcement_dismissal;
create table announcement_dismissal (
    organization_id uuid not null references organization(id),
    announcement_id uuid not null,
    account_id uuid not null references account(id),
    dismissed_at timestamptz not null default now(),
    primary key (announcement_id, account_id),
    foreign key (organization_id, announcement_id) references announcement(organization_id, id) on delete cascade
);
create index announcement_dismissal_by_account on announcement_dismissal (organization_id, account_id);

do $$
declare grantee_name text;
begin
    alter table announcement_dismissal enable row level security;
    alter table announcement_dismissal force row level security;
    create policy organization_scope on announcement_dismissal
        using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
        with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'announcement_dismissal' and privilege_type in ('DELETE', 'UPDATE')
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke delete, update on announcement_dismissal from %I', grantee_name);
    end loop;
end $$;
