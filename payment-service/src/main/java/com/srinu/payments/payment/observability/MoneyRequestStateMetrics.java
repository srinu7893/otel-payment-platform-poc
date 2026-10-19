package com.srinu.payments.payment.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MoneyRequestStateMetrics {
    private final JdbcTemplate jdbc;
    private volatile Map<String,Double> states=Map.of();
    private volatile double healthy=0,lastSuccess=0;
    private static final String[] STATUSES={"OPEN","PROCESSING","PAID","FAILED","CANCELLED","DECLINED"};
    @org.springframework.beans.factory.annotation.Autowired
    public MoneyRequestStateMetrics(DataSource source,MeterRegistry registry){this(new JdbcTemplate(source),registry);}
    MoneyRequestStateMetrics(JdbcTemplate jdbc,MeterRegistry registry){
        this.jdbc=jdbc;jdbc.setQueryTimeout(3);
        for(String status:STATUSES)Gauge.builder("poc.money.request.records",this,m->m.states.getOrDefault(status,Double.NaN)).tag("status",status).register(registry);
        Gauge.builder("poc.money.request.snapshot.healthy",this,m->m.healthy).register(registry);
        Gauge.builder("poc.money.request.snapshot.last.success.timestamp.seconds",this,m->m.lastSuccess).register(registry);
    }
    @Scheduled(fixedDelayString="${business.wallet-monitoring.refresh-ms:30000}")
    public void refresh(){
        try{
            Map<String,Double> next=new HashMap<>();for(String status:STATUSES)next.put(status,0.0);
            for(var row:jdbc.queryForList("select status,count(*) as records from payment.money_request group by status")){
                String status=(String)row.get("status");if(next.containsKey(status))next.put(status,((Number)row.get("records")).doubleValue());
            }
            states=Map.copyOf(next);healthy=1;lastSuccess=System.currentTimeMillis()/1000.0;
        }catch(RuntimeException error){healthy=0;org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=REQUEST_SNAPSHOT_FAILED errorType={}",error.getClass().getSimpleName());}
    }
}
