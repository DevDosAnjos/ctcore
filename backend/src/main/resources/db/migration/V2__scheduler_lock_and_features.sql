-- Trava das tarefas agendadas (ShedLock): só um servidor executa cada tarefa por disparo.
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);

-- Recursos (feature flags) por organização. Sem linha = desligado (RN-PNL-07).
-- Sem chave estrangeira nem RLS: a organização é da #13 e a política de acesso é da #14.
create table organization_feature (
    organization_id uuid         not null,
    feature_key     varchar(64)  not null,
    enabled         boolean      not null,
    updated_at      timestamptz  not null default now(),
    primary key (organization_id, feature_key)
);
