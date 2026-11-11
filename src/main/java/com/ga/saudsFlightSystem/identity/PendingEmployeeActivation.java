package com.ga.saudsFlightSystem.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class PendingEmployeeActivation {
    private final JdbcTemplate jdbc;
    private final IdentityServiceClient client;
    private static final Logger logger = Logger.getLogger(PendingEmployeeActivation.class.getName());

    private record Link(String key, Long userId) {}

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void activateLinkedAccounts() {
        List<Link> links = jdbc.query("""
                SELECT 'AIRLINE_EMPLOYEE:' || id AS key, user_id FROM airline_employees WHERE user_id IS NOT NULL
                UNION ALL SELECT 'AIRPORT_EMPLOYEE:' || id, user_id FROM airport_employees WHERE user_id IS NOT NULL
                UNION ALL SELECT 'MAINTENANCE_EMPLOYEE:' || id, user_id FROM maintenance_employees WHERE user_id IS NOT NULL
                UNION ALL SELECT 'ATC_EMPLOYEE:' || id, user_id FROM atc_employees WHERE user_id IS NOT NULL
                """, (row, index) -> new Link(row.getString("key"), row.getLong("user_id")));
        if (links.isEmpty()) return;
        try {
            var accounts = client.accounts(links.stream().map(Link::userId).distinct().toList());
            for (Link link : links) {
                AccountSnapshot account = accounts.get(link.userId());
                if (account != null && "PENDING_EMPLOYEE".equals(account.status())) client.activate(link.key(), link.userId());
            }
        } catch (RuntimeException ex) {
            logger.warning("Pending employee activation will retry when Identity is available.");
        }
    }
}
