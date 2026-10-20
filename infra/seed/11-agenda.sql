-- Synthetic no-class day (RF-AGE-01): one per seeded organization, 10 days from the seed date. The seed creates classes but no lessons: lessons come from the daily job (03:00 UTC), or when a class is created or its schedule changes in the app (see README, "Aulas na Agenda").
insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason)
select md5('ctcore-seed-noclass-' || o.n)::uuid, md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-unit-' || o.n)::uuid,
       current_date + 10, current_date + 10, 'Feriado sintético'
from generate_series(1, 3) as o(n)
on conflict do nothing;
