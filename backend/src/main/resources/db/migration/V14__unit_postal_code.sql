-- RF-ORG-03, RN-ORG-14: postal code (digits only) of the unit, used to find its latitude and longitude.
-- Revert (manual, no data loss outside this column):
--   alter table unit drop column postal_code;
alter table unit add column postal_code text check (postal_code ~ '^[0-9]{8}$');
