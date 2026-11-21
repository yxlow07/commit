-- Migration 0004: Calendar recurrence and reminder lead time for todos (M10)

ALTER TABLE todos ADD COLUMN IF NOT EXISTS recur_days integer;
ALTER TABLE todos ADD COLUMN IF NOT EXISTS reminder_lead_minutes integer;
