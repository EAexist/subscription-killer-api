
WITH app_user AS (
    -- 1. Insert the user, or if they exist, just select their ID
    INSERT INTO app_user (id, name, user_role)
    VALUES ('30f9a265-4f7d-3b1a-8e2b-7c4d5e6f8a9b', 'GUEST_APP_USER_NAME', 'USER')
    ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name -- Dummy update to force RETURNING
    RETURNING id
)

-- Insert GoogleAccount
INSERT INTO google_account (
    subject,
    name,
    email,
    app_user_id
)
SELECT new_accounts.subject, new_accounts.name, new_accounts.email, app_user.id
FROM app_user, (
    VALUES
        ('GUEST_APP_USER_SUBJECT_A', 'GUEST_APP_USER_NAME', 'guest_a@example.com')
) AS new_accounts(subject, name, email)
WHERE NOT EXISTS (
    SELECT 1 FROM google_account WHERE subject = new_accounts.subject
);