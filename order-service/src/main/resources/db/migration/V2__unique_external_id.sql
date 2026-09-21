-- Fail instead of silently deleting data if an existing database contains duplicates.
ALTER TABLE orders ADD CONSTRAINT uk_orders_external_id UNIQUE (external_id);
