/**
 * Processing is independent of HTTP entry points; API and scheduler stay in the same application.
 * A short transaction claims one PENDING row with FOR UPDATE SKIP LOCKED and commits PROCESSING.
 * The ERP call happens outside that transaction; a second transaction records its outcome.
 *
 * Production evolution, NOT implemented here: API -> durable queue -> independent Worker -> ERP.
 * The worker would own consumption, processing and idempotency/atomic claims. The broker would
 * support bounded retries, backoff, dead-letter queues and scaling independently of API traffic.
 * Publishing database changes reliably would require an outbox (or equivalent atomic handoff).
 *
 * Current limits: a process crash can leave PROCESSING rows requiring reconciliation. A timeout
 * does not prove that ERP rejected an order; local rollback cannot undo an external HTTP effect.
 * Durable ERP idempotency and reconciliation are prerequisites for safe automatic retries.
 */
package com.example.orders.processor;
