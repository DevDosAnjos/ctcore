-- RF-COB-02, RN-COB-09/10: who canceled and when (the reason uses the existing column) and the id that groups the renegotiated charges with the installments that replaced them.
-- No check "canceled status needs canceled_at": fixtures and older code set the status alone. The routes always set both.
-- Revert (manual): drop index charge_by_renegotiation; alter table charge drop column canceled_at, drop column canceled_by_account_id, drop column renegotiation_id;
alter table charge add column canceled_at timestamptz, add column canceled_by_account_id uuid references account(id), add column renegotiation_id uuid;
create index charge_by_renegotiation on charge (organization_id, renegotiation_id) where renegotiation_id is not null;
