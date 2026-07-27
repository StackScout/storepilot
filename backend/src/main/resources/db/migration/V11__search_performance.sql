-- pg_trgm lets a GIN index accelerate `lower(col) LIKE '%term%'` even with
-- a leading wildcard (a plain btree index can't be used for that pattern
-- at all). The index expression must match the query predicate exactly
-- (ProductSpecifications.matchesQuery / StoreService.search both filter on
-- lower(column)), so these are expression indexes on lower(...), not on
-- the raw column.
create extension if not exists pg_trgm;

create index idx_products_name_trgm on products using gin (lower(name) gin_trgm_ops);
create index idx_products_description_trgm on products using gin (lower(description) gin_trgm_ops);

create index idx_stores_name_trgm on stores using gin (lower(name) gin_trgm_ops);
create index idx_stores_tagline_trgm on stores using gin (lower(tagline) gin_trgm_ops);
create index idx_stores_city_trgm on stores using gin (lower(city) gin_trgm_ops);
