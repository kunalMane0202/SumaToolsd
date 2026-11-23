-- =====================================================================
-- V15 (Step 41b): a Super Admin may punch in again after punching out on the same day.
-- The time away is saved as an "OFF" row (not work and not break). Nothing is deleted.
-- Never edit this file after it has run.
-- =====================================================================
ALTER TABLE attendance_breaks ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'BREAK';
ALTER TABLE attendance_breaks ADD CONSTRAINT attendance_breaks_kind CHECK (kind IN ('BREAK', 'OFF'));
