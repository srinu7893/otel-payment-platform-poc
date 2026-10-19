package com.srinu.payments.bank.service;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
class DepositStateMetricsTest {
    @Test void committedRowsStayUniqueAndReadFailuresAreVisible(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:walletmetrics;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(source);
        jdbc.execute("create schema bank");jdbc.execute("create table bank.wallet_deposit(id int primary key,amount decimal(12,2))");
        var registry=new SimpleMeterRegistry();var metrics=new DepositStateMetrics(source,registry);
        assertTrue(Double.isNaN(registry.get("poc.wallet.deposit.records").gauge().value()));
        jdbc.update("insert into bank.wallet_deposit values(1,25)");metrics.refresh();metrics.refresh();
        assertEquals(1,registry.get("poc.wallet.deposit.records").gauge().value());
        assertEquals(25,registry.get("poc.wallet.deposit.amount").gauge().value());
        jdbc.execute("drop table bank.wallet_deposit");metrics.refresh();
        assertEquals(0,registry.get("poc.wallet.snapshot.healthy").gauge().value());
        assertEquals(1,registry.get("poc.wallet.deposit.records").gauge().value());
    }
}
