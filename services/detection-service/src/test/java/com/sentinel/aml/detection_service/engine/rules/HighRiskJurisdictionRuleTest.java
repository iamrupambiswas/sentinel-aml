package com.sentinel.aml.detection_service.engine.rules;

import com.sentinel.aml.detection_service.engine.RuleConfig;
import com.sentinel.aml.detection_service.engine.TransactionContext.Direction;
import com.sentinel.aml.detection_service.entity.WatchlistEntry;
import com.sentinel.aml.detection_service.repository.WatchlistEntryRepository;
import com.sentinel.aml.detection_service.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.sentinel.aml.detection_service.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighRiskJurisdictionRuleTest {

    private HighRiskJurisdictionRule rule;
    private final RuleConfig cfg = config("HIGH_RISK_JURISDICTION", "90", "0", 0, Map.of("nameSimilarity", 0.92));

    @BeforeEach
    void setUp() {
        WatchlistEntryRepository repo = mock(WatchlistEntryRepository.class);
        when(repo.findByActiveTrue()).thenReturn(List.of(
                entry("COUNTRY", "IR"), entry("COUNTRY", "IRN"), entry("NAME", "Zenith Shell Holdings Ltd")));
        WatchlistService watchlist = new WatchlistService(repo);
        watchlist.refresh();
        rule = new HighRiskJurisdictionRule(watchlist);
    }

    @Test
    void fires_for_listed_country_any_amount() {
        var e = event(ACCT, "EXT-Y", "100", "100", "INR", T0, "Some Trader", "IR");
        var hit = rule.evaluate(ctx(e, Direction.DEBIT), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().explanation()).contains("IR is on the high-risk country list");
    }

    @Test
    void country_match_is_case_insensitive_and_supports_alpha3() {
        var e = event(ACCT, "EXT-Y", "100", "100", "INR", T0, null, "irn");
        assertThat(rule.evaluate(ctx(e, Direction.DEBIT), cfg)).isPresent();
    }

    @Test
    void fuzzy_matches_misspelled_sanctioned_name() {
        var e = event(ACCT, "EXT-Y", "100", "100", "INR", T0, "Zenith Shel Holdings Ltd.", "AE");
        var hit = rule.evaluate(ctx(e, Direction.DEBIT), cfg);
        assertThat(hit).isPresent();
        assertThat(hit.get().explanation()).contains("ZENITH SHELL HOLDINGS LTD");
    }

    @Test
    void does_not_fire_for_clean_counterparty() {
        var e = event(ACCT, "EXT-Y", "100", "100", "INR", T0, "Acme Groceries Pvt Ltd", "IN");
        assertThat(rule.evaluate(ctx(e, Direction.DEBIT), cfg)).isEmpty();
    }

    @Test
    void does_not_fire_when_counterparty_data_missing() {
        var e = event(ACCT, "EXT-Y", "100", "100", "INR", T0, null, null);
        assertThat(rule.evaluate(ctx(e, Direction.DEBIT), cfg)).isEmpty();
    }

    private static WatchlistEntry entry(String type, String value) {
        WatchlistEntry e = new WatchlistEntry();
        e.setEntryType(type);
        e.setValue(value);
        e.setActive(true);
        return e;
    }
}
