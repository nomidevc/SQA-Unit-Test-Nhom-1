-- Make order_id nullable in product_reviews table
ALTER TABLE product_reviews MODIFY COLUMN order_id BIGINT NULL;
