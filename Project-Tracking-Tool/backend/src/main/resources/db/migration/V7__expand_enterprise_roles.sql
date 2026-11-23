ALTER TABLE app_users DROP CONSTRAINT ck_app_users_role;
ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_role
    CHECK (role IN (
        'SUPER_USER',
        'ADMIN',
        'MANAGER',
        'PROJECT_LEAD',
        'L1_SUPPORT',
        'L2_SUPPORT',
        'L3_SUPPORT',
        'TEAM_MEMBER'
    ));
