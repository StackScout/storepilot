-- Stage 1 of the product-search scalability plan: replace ranking-blind
-- substring matching with real relevance-ranked full-text search, without
-- introducing any new infrastructure. The existing gin trigram indexes
-- (V1) are kept and still used as a recall fallback for queries that don't
-- tokenize into a real lexeme match — see ProductRepository.searchFullText.
--
-- `to_tsvector('english', ...)` is IMMUTABLE when the language is a literal
-- ('english'), not a runtime lookup (e.g. get_current_ts_config()), which is
-- what makes it legal inside a STORED generated column. Weighted A > B so a
-- match in the product name ranks above the same term only appearing in the
-- description.
alter table products add column search_vector tsvector
  generated always as (
    setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'B')
  ) stored;

create index idx_products_search_vector on products using gin (search_vector);
