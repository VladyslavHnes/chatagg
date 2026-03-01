package com.chatagg.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenLibraryClient {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryClient.class);
    private static final String BASE_URL = "https://openlibrary.org";
    private static final long RATE_LIMIT_MS = 334; // ~3 req/sec

    private final HttpClient httpClient;
    private long lastRequestTime = 0;

    public record SearchResult(List<String> subjects, String authorKey) {}

    public OpenLibraryClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SearchResult searchByTitleAndAuthor(String title, String author) {
        try {
            rateLimit();

            String query = URLEncoder.encode(title + " " + author, StandardCharsets.UTF_8);
            String url = BASE_URL + "/search.json?q=" + query + "&limit=1&fields=subject,author_key";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Chatagg/1.0 (book aggregator)")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OpenLibrary search returned {}", response.statusCode());
                return new SearchResult(List.of(), null);
            }

            String body = response.body();
            List<String> subjects = extractJsonArray(body, "subject");
            String authorKey = extractFirstString(body, "author_key");

            return new SearchResult(subjects, authorKey);
        } catch (Exception e) {
            log.error("OpenLibrary search failed for '{}' by '{}'", title, author, e);
            return new SearchResult(List.of(), null);
        }
    }

    private List<String> extractJsonArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        // Simple regex extraction for JSON array of strings
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\\[([^\\]]*)]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            Pattern stringPattern = Pattern.compile("\"([^\"]+)\"");
            Matcher strMatcher = stringPattern.matcher(arrayContent);
            while (strMatcher.find() && result.size() < 5) {
                result.add(strMatcher.group(1));
            }
        }
        return result;
    }

    private String extractFirstString(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static final Set<String> FICTION_KEYWORDS = Set.of(
            "fiction", "novel", "fantasy", "science fiction", "mystery",
            "thriller", "romance", "horror", "poetry", "drama",
            "fable", "fairy tale", "short stories", "literary fiction",
            "detective", "adventure", "suspense", "dystopia", "satire",
            "comic", "manga", "graphic novel"
    );

    public static String classifyGenre(List<String> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return null;
        }
        for (String subject : subjects) {
            String lower = subject.toLowerCase();
            for (String keyword : FICTION_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return "Fiction";
                }
            }
        }
        return "Non-fiction";
    }

    private void rateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < RATE_LIMIT_MS) {
            Thread.sleep(RATE_LIMIT_MS - elapsed);
        }
        lastRequestTime = System.currentTimeMillis();
    }
}
