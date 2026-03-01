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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikidataClient {

    private static final Logger log = LoggerFactory.getLogger(WikidataClient.class);
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";

    private final HttpClient httpClient;

    public WikidataClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String lookupCountry(String wikidataId) {
        if (wikidataId == null || wikidataId.isBlank()) {
            return null;
        }

        try {
            // Get entity data with P27 (country of citizenship)
            String url = WIKIDATA_API + "?action=wbgetentities&ids=" +
                    URLEncoder.encode(wikidataId, StandardCharsets.UTF_8) +
                    "&props=claims&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Chatagg/1.0 (book aggregator)")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Wikidata lookup returned {}", response.statusCode());
                return null;
            }

            String body = response.body();

            // Extract P27 (country of citizenship) entity ID
            Pattern p27Pattern = Pattern.compile("\"P27\".*?\"id\"\\s*:\\s*\"(Q\\d+)\"");
            Matcher m = p27Pattern.matcher(body);
            if (!m.find()) {
                return null;
            }

            String countryId = m.group(1);
            return getEntityLabel(countryId);
        } catch (Exception e) {
            log.error("Wikidata lookup failed for '{}'", wikidataId, e);
            return null;
        }
    }

    public String searchByName(String authorName) {
        if (authorName == null || authorName.isBlank()) {
            return null;
        }

        // Try Ukrainian first, then English
        for (String lang : new String[]{"uk", "en"}) {
            try {
                String url = WIKIDATA_API + "?action=wbsearchentities&search=" +
                        URLEncoder.encode(authorName, StandardCharsets.UTF_8) +
                        "&language=" + lang + "&limit=1&format=json";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Chatagg/1.0 (book aggregator)")
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    continue;
                }

                String body = response.body();
                Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*\"(Q\\d+)\"");
                Matcher m = idPattern.matcher(body);
                if (m.find()) {
                    String country = lookupCountry(m.group(1));
                    if (country != null) {
                        return country;
                    }
                }
            } catch (Exception e) {
                log.error("Wikidata search failed for '{}' (lang={})", authorName, lang, e);
            }
        }

        return null;
    }

    private String getEntityLabel(String entityId) {
        try {
            String url = WIKIDATA_API + "?action=wbgetentities&ids=" +
                    URLEncoder.encode(entityId, StandardCharsets.UTF_8) +
                    "&props=labels&languages=en&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Chatagg/1.0 (book aggregator)")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return null;
            }

            String body = response.body();
            Pattern labelPattern = Pattern.compile("\"en\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = labelPattern.matcher(body);
            if (m.find()) {
                return m.group(1);
            }

            return null;
        } catch (Exception e) {
            log.error("Wikidata label lookup failed for '{}'", entityId, e);
            return null;
        }
    }
}
