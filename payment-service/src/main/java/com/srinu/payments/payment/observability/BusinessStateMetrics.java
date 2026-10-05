package com.srinu.payments.payment.observability;

import com.srinu.payments.payment.domain.PaymentStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/** Cached database truth, not counts inferred from sampled traces or repeated logs. */
@Component
@ConditionalOnProperty(name="business.monitoring.enabled", havingValue="true", matchIfMissing=true)
public class BusinessStateMetrics {
    private final JdbcTemplate jdbc;
    private volatile Map<String, Double> states = Map.of();
    private volatile double pending = Double.NaN, oldest = Double.NaN, lastSuccess = 0, healthy = 0;

    public BusinessStateMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (PaymentStatus status : PaymentStatus.values()) {
            Gauge.builder("poc.payment.records", this, m -> m.states.getOrDefault(status.name(), Double.NaN))
                .tag("status", status.name()).description("Current durable payment rows by status; not a counter").register(registry);
        }
        Gauge.builder("poc.outbox.pending", this, m -> m.pending).register(registry);
        Gauge.builder("poc.outbox.oldest.pending.seconds", this, m -> Double.isNaN(m.oldest) ? Double.NaN : m.oldest == 0 ? 0 : Math.max(0, System.currentTimeMillis()/1000.0-m.oldest)).register(registry);
        Gauge.builder("poc.business.snapshot.last.success.timestamp.seconds", this, m -> m.lastSuccess).register(registry);
        Gauge.builder("poc.business.snapshot.healthy", this, m -> m.healthy).register(registry);
    }

    @Scheduled(fixedDelayString="${business.monitoring.refresh-ms:10000}")
    public void refresh() {
        try {
            Map<String, Double> next = new HashMap<>();
            for (PaymentStatus status : PaymentStatus.values()) next.put(status.name(), 0.0);
            for (Map<String,Object> row : jdbc.queryForList("select status, count(*) as amount from payment.payments group by status")) {
                if (next.containsKey(row.get("status"))) next.put((String)row.get("status"), ((Number)row.get("amount")).doubleValue());
            }
            Map<String,Object> outbox = jdbc.queryForMap("select count(*) as amount, coalesce(extract(epoch from min(created_at)),0) as oldest from payment.outbox_event where status='PENDING'");
            double nextPending = ((Number)outbox.get("amount")).doubleValue();
            double nextOldest = ((Number)outbox.get("oldest")).doubleValue();
            states=Map.copyOf(next); pending=nextPending; oldest=nextOldest;
            lastSuccess=System.currentTimeMillis()/1000.0; healthy=1;
        } catch (RuntimeException error) {
            healthy=0;
            // Do not log SQL, credentials or exception messages from a failed connection.
            LoggerFactory.getLogger(getClass()).warn("event=BUSINESS_METRICS_REFRESH_FAILED component=payment errorType={}", error.getClass().getSimpleName());
        }
    }
}
