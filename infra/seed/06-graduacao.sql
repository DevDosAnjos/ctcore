-- Synthetic graduation levels (RF-MOD-02): copies the default to the seeded organizations with Jiu-Jitsu active; the first one has graduation on.
insert into graduation_level (id, organization_id, modality_id, position, name, color, secondary_color, max_degree, min_age, max_age, kids)
select md5('ctcore-seed-level-' || o.organization_id || '-' || d.position)::uuid, o.organization_id, d.modality_id, d.position, d.name, d.color,
       d.secondary_color, d.max_degree, d.min_age, d.max_age, d.kids
from organization_modality o join modality_default_level d on d.modality_id = o.modality_id
where o.active and not exists (select 1 from graduation_level g where g.organization_id = o.organization_id and g.modality_id = o.modality_id)
on conflict (id) do nothing;

update organization_modality set graduation_enabled = true
where organization_id = md5('ctcore-seed-org-1')::uuid and modality_id = (select id from modality where name = 'Jiu-Jitsu');
