-- #28: cor principal #rrggbb em minúsculas e titular só com dígitos (CPF 11 ou CNPJ 14); a validação completa fica no servidor.
-- logo_url passa a guardar só o nome do arquivo no armazenamento (nunca URL); a coluna não é renomeada.
-- Reversão: alter table organization drop constraint organization_color_format, drop constraint organization_holder_format;
alter table organization
    add constraint organization_color_format check (color is null or color ~ '^#[0-9a-f]{6}$'),
    add constraint organization_holder_format check (holder_document is null or holder_document ~ '^([0-9]{11}|[0-9]{14})$');
