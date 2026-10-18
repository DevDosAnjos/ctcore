-- RNF-15: optimistic version for the organization and unit settings forms (If-Match).
-- Rollback: alter table organization drop column version; alter table unit drop column version;
alter table organization add column version integer not null default 0;
alter table unit add column version integer not null default 0;
