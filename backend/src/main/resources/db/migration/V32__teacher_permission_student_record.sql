-- RF-ALU-03: fifth Teacher permission per unit, "ver ficha do aluno" (index 4), off by default; existing units get false.
-- Revert (manual): update unit_settings set teacher_permissions = teacher_permissions[1:4]; then restore the 4-position check and default.
alter table unit_settings drop constraint unit_settings_teacher_permissions_check;
update unit_settings set teacher_permissions = teacher_permissions || false where cardinality(teacher_permissions) = 4;
alter table unit_settings alter column teacher_permissions set default '{true,true,true,true,false}';
alter table unit_settings add constraint unit_settings_teacher_permissions_check check (
    cardinality(teacher_permissions) = 5 and array_position(teacher_permissions, null) is null);
