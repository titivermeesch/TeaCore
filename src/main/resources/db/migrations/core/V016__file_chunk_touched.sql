-- When a chunk was last written or reused. Cleanup only removes unreferenced
-- chunks that are old, so it can't delete the content of an upload that has
-- not committed its tea_files row yet.
ALTER TABLE tea_file_chunks ADD COLUMN touched_at BIGINT NOT NULL DEFAULT 0;
