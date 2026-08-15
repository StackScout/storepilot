-- Defense-in-depth alongside ProductService.requireUniqueSku's app-layer
-- check (which is case-insensitive; this constraint is exact-case,
-- matching the (store_id, slug) unique constraint's shape). Postgres
-- treats every NULL as distinct, so any number of products with no SKU
-- at all remain unaffected.
alter table products
    add constraint uq_products_store_id_sku unique (store_id, sku);
