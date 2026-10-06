package com.srinu.payments.notification.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

@Component
@EnableScheduling
@ConditionalOnProperty(name="business.monitoring.enabled", havingValue="true", matchIfMissing=true)
public class DeliveryStateMetrics {
    private static final String[] STATUSES={"RECEIVED","PROCESSING","RETRYING","SENT","FAILED"};
    private final JdbcTemplate jdbc;
    private volatile Map<String, Double> states=Map.of();
    private volatile double oldest=Double.NaN, mean=Double.NaN, lastSuccess=0, healthy=0;

    public DeliveryStateMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc=jdbc;
        for (String status : STATUSES) Gauge.builder("poc.notification.records", this, m -> m.states.getOrDefault(status, Double.NaN)).tag("status",status).register(registry);
        Gauge.builder("poc.notification.oldest.unsent.seconds",this,m -> Double.isNaN(m.oldest) ? Double.NaN : m.oldest==0 ? 0 : Math.max(0,System.currentTimeMillis()/1000.0-m.oldest)).register(registry);
        Gauge.builder("poc.notification.mean.received.to.sent.seconds",this,m -> m.mean).description("Mean consumer-record creation to sent, across stored sent rows; not payment-to-delivery latency").register(registry);
        Gauge.builder("poc.business.snapshot.last.success.timestamp.seconds",this,m -> m.lastSuccess).register(registry);
        Gauge.builder("poc.business.snapshot.healthy",this,m -> m.healthy).register(registry);
    }

    @Scheduled(fixedDelayString="${business.monitoring.refresh-ms:10000}")
    public void refresh() {
        try {
            Map<String,Double> next=new HashMap<>();
            for (String status : STATUSES) next.put(status,0.0);
            for (Map<String,Object> row : jdbc.queryForList("select status, count(*) as amount from notification.notifications group by status")) {
                if (next.containsKey(row.get("status"))) next.put((String)row.get("status"),((Number)row.get("amount")).doubleValue());
            }
            Map<String,Object> ages=jdbc.queryForMap("select coalesce(extract(epoch from min(created_at) filter (where status <> 'SENT')),0) as oldest, avg(extract(epoch from (sent_at-created_at))) filter (where status='SENT') as mean from notification.notifications");
            double nextOldest=((Number)ages.get("oldest")).doubleValue();
            double nextMean=ages.get("mean")==null ? Double.NaN : ((Number)ages.get("mean")).doubleValue();
            states=Map.copyOf(next); oldest=nextOldest; mean=nextMean;
            lastSuccess=System.currentTimeMillis()/1000.0; healthy=1;
        } catch (RuntimeException error) {
            healthy=0;
            LoggerFactory.getLogger(getClass()).warn("event=BUSINESS_METRICS_REFRESH_FAILED component=notification errorType={}",error.getClass().getSimpleName());
        }
    }
}
