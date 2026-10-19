package com.srinu.payments.payment.observability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MoneyRequestStateMetricsTest {
    @Test void boundedCommittedStatesStayStableAcrossRefreshesAndFailures(){
        var jdbc=mock(JdbcTemplate.class);var meters=new SimpleMeterRegistry();var metrics=new MoneyRequestStateMetrics(jdbc,meters);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("status","PAID","records",3L),Map.of("status","UNTRUSTED","records",100L)));
        metrics.refresh();metrics.refresh();
        assertEquals(6,meters.find("poc.money.request.records").gauges().size());
        assertEquals(3,meters.get("poc.money.request.records").tag("status","PAID").gauge().value());
        assertEquals(1,meters.get("poc.money.request.snapshot.healthy").gauge().value());
        when(jdbc.queryForList(anyString())).thenThrow(new IllegalStateException("SECRET"));metrics.refresh();
        assertEquals(0,meters.get("poc.money.request.snapshot.healthy").gauge().value());
        assertEquals(3,meters.get("poc.money.request.records").tag("status","PAID").gauge().value());
    }
}
