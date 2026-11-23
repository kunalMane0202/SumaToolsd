-- =====================================================================
-- V10: Client Management screen (approved by the user on 5 Nov 2026)
--   The client form has: Name, Designation, Company Email ID, Contact Number,
--   Project Name, Account Manager (assigned yes/no + who).
--   Only "designation" is new; the other fields already exist
--   (client_name, email, contact_number, projects, account_manager_id).
-- Never edit this file after it has run.
-- =====================================================================

ALTER TABLE clients ADD COLUMN designation VARCHAR(100);