-- Optimistic locking for alerts.
--
-- AlertRepositoryAdapter.save() writes every column, so two concurrent writers
-- silently clobbered each other: resolving an alert while a vote-count sync was in
-- flight (or vice versa) meant whichever committed last won, discarding the other's
-- change. A @Version column turns that into an OptimisticLockException the caller
-- can retry.
ALTER TABLE alerts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
