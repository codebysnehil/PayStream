-- Fictional demo accounts (bank code PAYS does not exist).
INSERT INTO accounts (account_number, holder_name, currency, balance, updated_at, version) VALUES
    ('GB82PAYS10000000000001', 'Northwind Treasury',     'GBP', 5000000.00, CURRENT_TIMESTAMP, 0),
    ('GB82PAYS10000000000002', 'Contoso Payroll',        'GBP', 2500000.00, CURRENT_TIMESTAMP, 0),
    ('GB82PAYS10000000000003', 'Fabrikam Suppliers',     'GBP',  750000.00, CURRENT_TIMESTAMP, 0),
    ('GB82PAYS10000000000004', 'Adventure Works Retail', 'GBP',     500.00, CURRENT_TIMESTAMP, 0),
    ('GB82PAYS10000000000005', 'Tailspin Toys',          'GBP',       0.00, CURRENT_TIMESTAMP, 0),
    ('DE89PAYS20000000000001', 'Litware GmbH',           'EUR', 1000000.00, CURRENT_TIMESTAMP, 0);
