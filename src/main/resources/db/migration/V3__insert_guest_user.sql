
-- Insert AppUser only if guest GoogleAccount doesn't exist
WITH existing AS (
    SELECT 1
    FROM google_account
    WHERE subject = 'GUEST_APP_USER_SUBJECT'
)
INSERT INTO app_user (id, name, user_role)
SELECT gen_random_uuid(), 'GUEST_APP_USER_NAME', 'USER'
WHERE NOT EXISTS (SELECT 1 FROM existing)
RETURNING id;

-- Insert GoogleAccount
INSERT INTO google_account (
    subject,
    name,
    email,
    app_user_id
)
SELECT
    'GUEST_APP_USER_SUBJECT',
    'GUEST_APP_USER_NAME',
    'guest@example.com',
    id
FROM app_user
WHERE name = 'GUEST_APP_USER_NAME'
  AND NOT EXISTS (
      SELECT 1 FROM google_account WHERE subject = 'GUEST_APP_USER_SUBJECT'
  );