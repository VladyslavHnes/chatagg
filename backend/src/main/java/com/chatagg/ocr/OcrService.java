package com.chatagg.ocr;

import com.chatagg.config.AppConfig;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final Tesseract tesseract;
    private final int confidenceThreshold;

    public record OcrResult(String text, float confidence) {
        public boolean isFlagged() {
            return confidence < 0;
        }
    }

    public OcrService(AppConfig config) {
        this.confidenceThreshold = config.ocrConfidenceThreshold;
        this.tesseract = new Tesseract();
        tesseract.setDatapath(config.tesseractDataPath);
        tesseract.setLanguage("ukr+eng");
        tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
        tesseract.setVariable("user_defined_dpi", "300");
    }

    public OcrResult extractText(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new OcrResult("", 0f);
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("Could not decode image ({} bytes)", imageBytes.length);
                return new OcrResult("", 0f);
            }

            String text = tesseract.doOCR(image).trim();
            // Tess4J doesn't expose per-page confidence easily,
            // so we estimate based on text quality heuristics
            float confidence = estimateConfidence(text);

            if (confidence < confidenceThreshold) {
                log.info("Low OCR confidence ({}) for extracted text (length={})", confidence, text.length());
            }

            return new OcrResult(text, confidence);
        } catch (TesseractException e) {
            log.error("OCR extraction failed", e);
            return new OcrResult("", 0f);
        } catch (IOException e) {
            log.error("Failed to read image bytes", e);
            return new OcrResult("", 0f);
        } catch (Throwable e) {
            log.warn("OCR unavailable ({}), skipping", e.getMessage());
            return new OcrResult("", 0f);
        }
    }

    private float estimateConfidence(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }

        // Heuristic: ratio of alphanumeric + common punctuation to total chars
        int total = text.length();
        int meaningful = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)
                    || c == '.' || c == ',' || c == '!' || c == '?' || c == '-'
                    || c == '\'' || c == '"' || c == ':' || c == ';') {
                meaningful++;
            }
        }

        float ratio = (float) meaningful / total * 100;
        // Also penalize very short texts
        if (total < 10) {
            ratio *= 0.5f;
        }

        return Math.min(ratio, 100f);
    }
}
