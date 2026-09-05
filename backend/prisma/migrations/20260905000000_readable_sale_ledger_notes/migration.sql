-- A sale's cash-register entry used to be noted as "Sale <uuid>", which read
-- as a line of unreadable hex on the one screen the owner scans by eye. The
-- entry has carried its saleId as a column all along, so the id in the text
-- was never doing any work.
--
-- Rewrites the notes already recorded. Matched on shape rather than on a
-- prefix, so a note somebody typed themselves that merely starts with the
-- word "Sale" is left alone.

UPDATE "CashRegisterEntry" e
SET note = CASE
             WHEN btrim(coalesce(s."customerName", '')) <> ''
               THEN 'Sale to ' || btrim(s."customerName")
             ELSE 'Sale'
           END
FROM "Sale" s
WHERE e."saleId" = s.id
  AND e.note ~ '^Sale [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

UPDATE "CashRegisterEntry" e
SET note = CASE
             WHEN btrim(coalesce(s."customerName", '')) <> ''
               THEN 'Payment received from ' || btrim(s."customerName")
             ELSE 'Payment received'
           END
FROM "Sale" s
WHERE e."saleId" = s.id
  AND e.note ~ '^Payment received for sale [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

-- Anything left over is an entry whose sale is gone; it still shouldn't show
-- a uuid.
UPDATE "CashRegisterEntry"
SET note = 'Sale'
WHERE note ~ '^Sale [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

UPDATE "CashRegisterEntry"
SET note = 'Payment received'
WHERE note ~ '^Payment received for sale [0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';
