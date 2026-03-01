package com.chatagg.unit;

import com.chatagg.config.AppConfig;
import com.chatagg.ocr.OcrService;
import com.chatagg.ocr.OcrService.OcrResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcrServiceTest {

    private AppConfig config;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.tesseractDataPath = "/usr/share/tesseract-ocr/5/tessdata";
        config.ocrConfidenceThreshold = 70;
    }

    // --- OcrResult record tests ---

    @Test
    void ocrResult_holdsEnglishText() {
        OcrResult result = new OcrResult("The quick brown fox jumps over the lazy dog", 95.0f);

        assertEquals("The quick brown fox jumps over the lazy dog", result.text());
        assertEquals(95.0f, result.confidence(), 0.01f);
    }

    @Test
    void ocrResult_holdsCyrillicText() {
        OcrResult result = new OcrResult(
                "\u0426\u0435 \u0442\u0435\u043a\u0441\u0442 \u0443\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u043e\u044e \u043c\u043e\u0432\u043e\u044e", 88.5f);

        assertEquals("\u0426\u0435 \u0442\u0435\u043a\u0441\u0442 \u0443\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u043e\u044e \u043c\u043e\u0432\u043e\u044e", result.text());
        assertEquals(88.5f, result.confidence(), 0.01f);
    }

    @Test
    void ocrResult_confidenceScore_withinExpectedRange() {
        OcrResult highConfidence = new OcrResult("clear text", 99.0f);
        OcrResult lowConfidence = new OcrResult("blurry text", 30.0f);
        OcrResult zeroConfidence = new OcrResult("", 0.0f);

        assertTrue(highConfidence.confidence() >= 0.0f && highConfidence.confidence() <= 100.0f);
        assertTrue(lowConfidence.confidence() >= 0.0f && lowConfidence.confidence() <= 100.0f);
        assertEquals(0.0f, zeroConfidence.confidence(), 0.01f);
    }

    @Test
    void ocrResult_belowThreshold_isFlagged() {
        int threshold = config.ocrConfidenceThreshold; // 70

        OcrResult lowResult = new OcrResult("barely readable", 45.0f);
        OcrResult borderlineResult = new OcrResult("somewhat readable", 70.0f);
        OcrResult goodResult = new OcrResult("clearly readable", 95.0f);

        assertTrue(lowResult.confidence() < threshold,
                "Result with confidence 45 should be below threshold 70");
        assertFalse(borderlineResult.confidence() < threshold,
                "Result with confidence exactly at threshold should not be flagged");
        assertFalse(goodResult.confidence() < threshold,
                "Result with confidence 95 should not be flagged");
    }

    @Test
    void ocrResult_emptyText_forUnreadableImage() {
        OcrResult result = new OcrResult("", 0.0f);

        assertEquals("", result.text());
        assertEquals(0.0f, result.confidence(), 0.01f);
        assertTrue(result.text().isEmpty());
    }

    @Test
    void ocrResult_nullText_handledGracefully() {
        // OcrResult is a record, so null is a valid value for text
        OcrResult result = new OcrResult(null, 0.0f);

        assertNull(result.text());
        assertEquals(0.0f, result.confidence(), 0.01f);
    }

    @Test
    void ocrResult_recordEquality() {
        OcrResult a = new OcrResult("same text", 85.0f);
        OcrResult b = new OcrResult("same text", 85.0f);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void ocrResult_recordInequality() {
        OcrResult a = new OcrResult("text a", 85.0f);
        OcrResult b = new OcrResult("text b", 85.0f);
        OcrResult c = new OcrResult("text a", 60.0f);

        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }

    // --- OcrService constructor tests ---

    @Test
    void constructor_withValidConfig_doesNotThrow() {
        // OcrService constructor should accept AppConfig without throwing,
        // even though Tesseract native libs may not be available at test time.
        // If the constructor defers native initialization, this will pass.
        // If it eagerly initializes Tesseract, this test documents that expectation.
        assertDoesNotThrow(() -> new OcrService(config));
    }

    @Test
    void constructor_withCustomDataPath() {
        config.tesseractDataPath = "/custom/tessdata/path";
        config.ocrConfidenceThreshold = 50;

        // Constructor should accept any path string without immediate validation
        assertDoesNotThrow(() -> new OcrService(config));
    }

    @Test
    void constructor_withZeroThreshold() {
        config.ocrConfidenceThreshold = 0;

        assertDoesNotThrow(() -> new OcrService(config));
    }

    @Test
    void constructor_withMaxThreshold() {
        config.ocrConfidenceThreshold = 100;

        assertDoesNotThrow(() -> new OcrService(config));
    }
}
