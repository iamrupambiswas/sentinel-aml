package com.sentinel.aml.detection_service.service;

import com.sentinel.aml.detection_service.entity.WatchlistEntry;
import com.sentinel.aml.detection_service.repository.WatchlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory copy of the watchlist (a few thousand entries at most), refreshed periodically.
 * Name matching is fuzzy (Jaro-Winkler) so "Zenith Shel Holdings Ltd." still matches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private static final JaroWinklerSimilarity JW = new JaroWinklerSimilarity();

    private final WatchlistEntryRepository repository;
    private volatile Set<String> countries = Set.of();
    private volatile List<String> names = List.of();

    public record NameMatch(String listedName, double score) {}

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRateString = "${sentinel.watchlist.refresh-ms:300000}", initialDelayString = "${sentinel.watchlist.refresh-ms:300000}")
    public void refresh() {
        Set<String> c = new HashSet<>();
        Set<String> n = new HashSet<>();
        for (WatchlistEntry e : repository.findByActiveTrue()) {
            if ("COUNTRY".equalsIgnoreCase(e.getEntryType())) c.add(e.getValue().toUpperCase(Locale.ROOT));
            else if ("NAME".equalsIgnoreCase(e.getEntryType())) n.add(normalize(e.getValue()));
        }
        countries = Set.copyOf(c);
        names = List.copyOf(n);
        log.info("Watchlist loaded: {} countries, {} names", countries.size(), names.size());
    }

    public boolean isHighRiskCountry(String country) {
        return country != null && countries.contains(country.trim().toUpperCase(Locale.ROOT));
    }

    public Optional<NameMatch> bestNameMatch(String candidate, double minSimilarity) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(candidate);
        NameMatch best = null;
        for (String listed : names) {
            double score = JW.apply(normalized, listed);
            if (score >= minSimilarity && (best == null || score > best.score())) {
                best = new NameMatch(listed, score);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Upper-case, strip accents and punctuation, collapse whitespace. */
    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
