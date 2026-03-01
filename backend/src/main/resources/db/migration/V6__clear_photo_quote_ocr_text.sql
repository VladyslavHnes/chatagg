-- V6: Clear OCR text from photo quotes (OCR is no longer used)
UPDATE quote SET text_content = '' WHERE source_type = 'photo' AND text_content != '';
