-- Semilla fija (las convenciones internas de API). Mismos IDs en staging y prod.

INSERT INTO merchants (id, name, account_id, api_key_hash, created_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Almacén Bruno', 'bbbbbbbb-0000-4000-8000-000000000201', NULL, now()),
    ('22222222-2222-2222-2222-222222222222', 'Café Cauri',    'bbbbbbbb-0000-4000-8000-000000000202', NULL, now());
