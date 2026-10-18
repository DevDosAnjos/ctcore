-- RF-ORG-04, RN-ORG-14: the unit CNPJ is kept as 14 digits only (the check digits are validated by the application).
-- Revert (manual, no data loss): alter table unit drop constraint unit_cnpj_digits;
alter table unit add constraint unit_cnpj_digits check (cnpj ~ '^[0-9]{14}$');
