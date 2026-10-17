-- RF-ACE-07: instant of the last session started (login, invite acceptance, password reset); null until the first one.
alter table account add column last_access_at timestamptz;
