-- RN-ORG-06: no máximo um Coordenador ativo por organização.
create unique index account_link_one_active_coordinator on account_link (organization_id)
    where role = 'COORDINATOR' and status = 'ACTIVE';

-- Novo modelo de e-mail: confirmação da transferência da coordenação.
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED'));
