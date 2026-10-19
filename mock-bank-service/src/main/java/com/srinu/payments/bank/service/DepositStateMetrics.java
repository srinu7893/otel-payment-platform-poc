package com.srinu.payments.bank.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Committed rows make replays/restarts unable to inflate completed deposits. */
@Component
@EnableScheduling
public class DepositStateMetrics {
    private final JdbcTemplate jdbc;
    private volatile double count=Double.NaN,total=Double.NaN,healthy=0,lastSuccess=0;
    public DepositStateMetrics(DataSource source,MeterRegistry registry){
        this.jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(3);
        Gauge.builder("poc.wallet.deposit.records",this,m->m.count).register(registry);
        Gauge.builder("poc.wallet.deposit.amount",this,m->m.total).tag("currency","INR").register(registry);
        Gauge.builder("poc.wallet.snapshot.healthy",this,m->m.healthy).register(registry);
        Gauge.builder("poc.wallet.snapshot.last.success.timestamp.seconds",this,m->m.lastSuccess).register(registry);
    }
    @Scheduled(fixedDelayString="${business.wallet-monitoring.refresh-ms:30000}")
    public void refresh(){
        try{
            Map<String,Object> row=jdbc.queryForMap("select count(*) as records,coalesce(sum(amount),0) as total from bank.wallet_deposit");
            count=((Number)row.get("records")).doubleValue();total=((Number)row.get("total")).doubleValue();
            healthy=1;lastSuccess=System.currentTimeMillis()/1000.0;
        }catch(RuntimeException error){
            healthy=0;
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=WALLET_SNAPSHOT_FAILED errorType={}",error.getClass().getSimpleName());
        }
    }
}
