-- RN-NOT-05 (#447): notifications are deleted 30 days after creation. The runtime role has no DELETE on the table (V47), so the daily job calls this
-- function, which can only remove expired rows of the organization it is given; the search path is fixed so it cannot be hijacked.
-- Revert (manual): drop function purge_expired_notifications(uuid, timestamptz);
create function purge_expired_notifications(p_org uuid, p_cutoff timestamptz) returns integer
language sql security definer set search_path = public, pg_temp
as $$ with gone as (delete from notification where organization_id = p_org and created_at <= p_cutoff returning 1) select count(*)::integer from gone $$;
revoke all on function purge_expired_notifications(uuid, timestamptz) from public;
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'notification' and privilege_type = 'SELECT'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('grant execute on function purge_expired_notifications(uuid, timestamptz) to %I', grantee_name);
    end loop;
end $$;
