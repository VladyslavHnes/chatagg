package com.chatagg.config;

public class AppConfig {

    public String dbHost;
    public int dbPort;
    public String dbName;
    public String dbUser;
    public String dbPassword;
    public String appPassword;
    public int appPort;
    public int telegramApiId;
    public String telegramApiHash;
    public String telegramPhone;
    public long telegramChannelId;
    public String tesseractDataPath;
    public int ocrConfidenceThreshold;
    public String photoStoragePath;

    public AppConfig() {
        this.dbHost = env("DB_HOST", "localhost");
        this.dbPort = Integer.parseInt(env("DB_PORT", "5432"));
        this.dbName = env("DB_NAME", "chatagg");
        this.dbUser = env("DB_USER", "chatagg");
        this.dbPassword = env("DB_PASSWORD", "devpass");
        this.appPassword = env("APP_PASSWORD", "devpass");
        this.appPort = Integer.parseInt(env("APP_PORT", "7070"));
        this.telegramApiId = Integer.parseInt(env("TELEGRAM_API_ID", "0"));
        this.telegramApiHash = env("TELEGRAM_API_HASH", "");
        this.telegramPhone = env("TELEGRAM_PHONE", "");
        this.telegramChannelId = Long.parseLong(env("TELEGRAM_CHANNEL_ID", "0"));
        this.tesseractDataPath = env("TESSERACT_DATA_PATH", "/usr/share/tesseract-ocr/5/tessdata");
        this.ocrConfidenceThreshold = Integer.parseInt(env("OCR_CONFIDENCE_THRESHOLD", "70"));
        this.photoStoragePath = env("PHOTO_STORAGE_PATH", "photos");
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
