package com.srinu.payments.payment.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/** PoC shared-database reporting, explicitly enabled only where both schemas exist. */
@Component
@ConditionalOnProperty(name="business.domain-monitoring.enabled", havingValue="true")
public class DomainOutcomeMetrics {
    private static final String[] OPERATIONS={"payment","transfer","refund"};
    private static final String[] STATUSES={"PENDING","PROCESSING","RECONCILIATION_REQUIRED","COMPLETED","DECLINED","FAILED","CANCELLED"};
    private final JdbcTemplate jdbc;
    private volatile Map<String,Double> snapshot=Map.of();
    private volatile double healthy=0, lastSuccess=0;

    @Autowired
    public DomainOutcomeMetrics(DataSource source, MeterRegistry registry) {
        this(new JdbcTemplate(source),registry);
    }

    DomainOutcomeMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc=jdbc;
        // Dedicated template: do not change timeout behavior for payment transactions.
        jdbc.setQueryTimeout(3);
        for(String operation:OPERATIONS) {
            for(String status:STATUSES) Gauge.builder("poc.domain.records",this,m->m.value(operation+":"+status))
                .tag("operation",operation).tag("status",status).register(registry);
            Gauge.builder("poc.domain.cohort.records",this,m->m.value(operation+":cohort")).tag("operation",operation).register(registry);
            Gauge.builder("poc.domain.cohort.completed",this,m->m.value(operation+":completed")).tag("operation",operation).register(registry);
        }
        for(String percentile:new String[]{"0.5","0.95","0.99"}) Gauge.builder("poc.event.delivery.seconds",this,m->m.value("delivery:"+percentile))
            .tag("quantile",percentile).description("Stored outbox creation to SENT, including queue delay; last hour of sent records").register(registry);
        Gauge.builder("poc.event.delivery.records",this,m->m.value("delivery:count")).register(registry);
        Gauge.builder("poc.domain.snapshot.healthy",this,m->m.healthy).register(registry);
        Gauge.builder("poc.domain.snapshot.last.success.timestamp.seconds",this,m->m.lastSuccess).register(registry);
    }
    private double value(String key){return snapshot.getOrDefault(key,Double.NaN);}

    @Scheduled(fixedDelayString="${business.domain-monitoring.refresh-ms:30000}")
    public void refresh(){
        try{
            Map<String,Double> next=new HashMap<>();
            for(String operation:OPERATIONS){
                for(String status:STATUSES) next.put(operation+":"+status,0.0);
                // Table names come exclusively from the fixed OPERATIONS list.
                String table="payment."+operation+"s";
                for(var row:jdbc.queryForList("select status,count(*) as amount from "+table+" group by status")){
                    String key=operation+":"+row.get("status");
                    if(next.containsKey(key))next.put(key,((Number)row.get("amount")).doubleValue());
                }
                var cohort=jdbc.queryForMap("select count(*) as amount,count(*) filter(where status='COMPLETED') as completed from "+table+" where created_at >= now()-interval '1 hour' and created_at < now()-interval '2 minutes'");
                next.put(operation+":cohort",((Number)cohort.get("amount")).doubleValue());
                next.put(operation+":completed",((Number)cohort.get("completed")).doubleValue());
            }
            var delivery=jdbc.queryForMap("select count(*) as amount,percentile_cont(0.5) within group(order by extract(epoch from(n.sent_at-o.created_at))) as p50,percentile_cont(0.95) within group(order by extract(epoch from(n.sent_at-o.created_at))) as p95,percentile_cont(0.99) within group(order by extract(epoch from(n.sent_at-o.created_at))) as p99 from notification.notifications n join payment.outbox_event o on o.id=n.source_event_id where n.status='SENT' and n.sent_at >= now()-interval '1 hour' and n.sent_at >= o.created_at");
            next.put("delivery:count",((Number)delivery.get("amount")).doubleValue());
            String[] keys={"p50","p95","p99"};String[] quantiles={"0.5","0.95","0.99"};
            for(int i=0;i<keys.length;i++)next.put("delivery:"+quantiles[i],delivery.get(keys[i])==null?Double.NaN:((Number)delivery.get(keys[i])).doubleValue());
            snapshot=Map.copyOf(next);healthy=1;lastSuccess=System.currentTimeMillis()/1000.0;
        }catch(RuntimeException error){healthy=0;LoggerFactory.getLogger(getClass()).warn("event=DOMAIN_METRICS_REFRESH_FAILED errorType={}",error.getClass().getSimpleName());}
    }
}
