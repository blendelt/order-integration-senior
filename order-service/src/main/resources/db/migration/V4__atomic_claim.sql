ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_orders_pending ON orders (created_at, id) WHERE status = 'PENDING';
