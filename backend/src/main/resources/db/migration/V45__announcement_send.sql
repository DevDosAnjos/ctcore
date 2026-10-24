-- RF-COM-02 (#82): criar e enviar comunicado. announcement ganha imagem, "fixar até" e a ligação com o dia sem aula; o destino CLASSES (varias turmas, um
-- comunicado so, RN-AGE-10) usa announcement_class; o papel operacional volta a ter UPDATE (o envio grava sent_at) e o gatilho
-- announcement_sent_is_final recusa mudar ou apagar comunicado enviado (RN-COM-06). O dono da tabela (manutencao) nao e barrado.
-- Revert (manual): drop trigger announcement_sent_is_final on announcement; drop function announcement_sent_is_final(); drop table announcement_class;
-- drop index announcement_by_unit_date; drop index announcement_one_per_day; alter table announcement drop column no_class_day_id, drop column pinned_until, drop column image_name;
-- restore announcement_target_check and announcement_target_kind_check without CLASSES (V44 text); revoke update on announcement from the runtime role.
alter table announcement add column image_name text;
alter table announcement add column pinned_until date;
alter table announcement add column no_class_day_id uuid;
alter table announcement add foreign key (organization_id, no_class_day_id) references no_class_day(organization_id, id) on delete set null (no_class_day_id);
create unique index announcement_one_per_day on announcement (no_class_day_id) where no_class_day_id is not null;

alter table announcement drop constraint announcement_target_kind_check;
alter table announcement add constraint announcement_target_kind_check check (target_kind in ('CLASS', 'CLASSES', 'MODALITY', 'UNIT', 'ORGANIZATION'));
alter table announcement drop constraint announcement_target_check;
alter table announcement add constraint announcement_target_check check (
    (target_kind = 'CLASS' and class_id is not null and modality_id is null and unit_id is not null)
    or (target_kind = 'CLASSES' and class_id is null and modality_id is null and unit_id is not null)
    or (target_kind = 'MODALITY' and modality_id is not null and class_id is null)
    or (target_kind = 'UNIT' and unit_id is not null and class_id is null and modality_id is null)
    or (target_kind = 'ORGANIZATION' and unit_id is null and class_id is null and modality_id is null));

create index announcement_by_unit_date on announcement (organization_id, unit_id, (coalesce(sent_at, scheduled_for)));

create table announcement_class (
    organization_id uuid not null references organization(id),
    announcement_id uuid not null,
    class_id uuid not null,
    primary key (announcement_id, class_id),
    foreign key (organization_id, announcement_id) references announcement(organization_id, id) on delete cascade,
    foreign key (organization_id, class_id) references training_class(organization_id, id)
);
create index announcement_class_by_class on announcement_class (organization_id, class_id);
alter table announcement_class enable row level security;
alter table announcement_class force row level security;
create policy organization_scope on announcement_class
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Linha enviada nao muda nem some, fora o proprio envio (sent_at nulo -> preenchido) e o dia sem aula apagado (so no_class_day_id vai a nulo).
create function announcement_sent_is_final() returns trigger language plpgsql as $$
begin
    if pg_has_role(current_user, (select relowner from pg_class where oid = tg_relid), 'USAGE') or old.sent_at is null then
        return case when tg_op = 'DELETE' then old else new end;
    end if;
    if tg_op = 'UPDATE' and new.no_class_day_id is null and to_jsonb(new) - 'no_class_day_id' = to_jsonb(old) - 'no_class_day_id' then
        return new;
    end if;
    raise exception 'announcement already sent' using errcode = '23514';
end $$;
create trigger announcement_sent_is_final before update or delete on announcement
    for each row execute function announcement_sent_is_final();

-- UPDATE volta ao papel operacional (a V44 o retirou); announcement_class nao tem UPDATE.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'announcement' and privilege_type = 'DELETE'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('grant update on announcement to %I', grantee_name);
    end loop;
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'announcement_class' and privilege_type = 'UPDATE'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke update on announcement_class from %I', grantee_name);
    end loop;
end $$;
