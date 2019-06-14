ALTER TABLE holdings_status
ADD COLUMN IF NOT EXISTS statusDetails VARCHAR (500);
