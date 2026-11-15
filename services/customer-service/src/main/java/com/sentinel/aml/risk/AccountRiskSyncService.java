package com.sentinel.aml.risk;

import com.sentinel.aml.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full resync of every account's effective rating into Redis: on startup, after seeding and on demand.
 * Pages through accounts 500 at a time (one pipelined Redis call per page), so 8,000 seeded accounts
 * take well under a second and memory stays flat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountRiskSyncService {

    private static final int PAGE = 500;

    private final AccountRepository accountRepository;
    private final AccountRiskPublisher publisher;

    @Value("${sentinel.risk.sync-on-startup:true}")
    private boolean syncOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!syncOnStartup) return;
        try {
            syncAll();
        } catch (Exception e) {
            // Never block startup on Redis; the admin endpoint can resync later
            log.error("Startup risk sync failed; run POST /api/v1/admin/risk-sync once Redis is up", e);
        }
    }

    @Transactional(readOnly = true)
    public int syncAll() {
        long start = System.currentTimeMillis();
        int total = 0;
        Slice<AccountRepository.AccountRiskRow> slice;
        int page = 0;
        do {
            slice = accountRepository.findRiskRows(PageRequest.of(page++, PAGE, Sort.by("id")));
            Map<String, com.sentinel.aml.enums.RiskRating> batch = new LinkedHashMap<>();
            for (AccountRepository.AccountRiskRow row : slice) {
                batch.put(row.getAccountId(), RiskRatings.effective(row.getCustomerRating(), row.getAccountRating()));
            }
            publisher.publishNow(batch);
            total += batch.size();
        } while (slice.hasNext());
        log.info("Synced effective risk rating of {} account(s) to Redis in {} ms", total, System.currentTimeMillis() - start);
        return total;
    }
}
