-- Existing published news keeps its status; missing official URLs must be supplied before republishing.
-- No historical URL or cover is guessed. Apply to an existing database only after explicit approval.
ALTER TABLE content
 ADD COLUMN official_url VARCHAR(2048) DEFAULT NULL,
 ADD COLUMN carousel_enabled TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
