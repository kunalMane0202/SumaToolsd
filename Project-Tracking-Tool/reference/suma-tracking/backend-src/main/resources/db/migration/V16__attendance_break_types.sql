-- =====================================================================
-- V16 (Step 41c): every break has a type: Tea, Lunch, Dinner or Refreshment.
-- All types share the same daily break time (for example 1 hour in total).
-- Older breaks keep an empty type. Never edit this file after it has run.
-- =====================================================================
ALTER TABLE attendance_breaks ADD COLUMN break_type VARCHAR(20);
ALTER TABLE attendance_breaks ADD CONSTRAINT attendance_breaks_type
    CHECK (break_type IS NULL OR break_type IN ('TEA', 'LUNCH', 'DINNER', 'REFRESHMENT'));
