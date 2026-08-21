-- Admin logins are no longer audit-logged (see AuthController.login) —
-- one low-value row per session, crowding out substantive activity on the
-- admin overview page with no compliance/security need it actually serves.
-- Purge what's already accumulated rather than leaving dead data behind.
delete from audit_logs where action = 'admin_login';
