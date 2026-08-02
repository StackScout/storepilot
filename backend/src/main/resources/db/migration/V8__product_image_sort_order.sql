-- Explicit ordering for product images — see ProductImage.sortOrder's doc
-- comment. Previously relied on created_at, which isn't a reliable
-- distinguisher between images uploaded in the same request. index 0
-- (lowest sort_order) is the product's primary image, shown as the
-- thumbnail everywhere.
alter table product_images
    add column sort_order integer not null default 0;

update product_images pi
set sort_order = ranked.rn
from (
    select id, row_number() over (partition by product_id order by created_at asc) - 1 as rn
    from product_images
) ranked
where pi.id = ranked.id;
