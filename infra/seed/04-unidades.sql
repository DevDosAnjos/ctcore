-- Synthetic cities for the seeded units (RF-ORG-02).
update unit set city = 'Cidade Fictícia ' || right(short_name, 1)
where short_name in ('ficticia-1', 'ficticia-2', 'ficticia-3') and city is null;
