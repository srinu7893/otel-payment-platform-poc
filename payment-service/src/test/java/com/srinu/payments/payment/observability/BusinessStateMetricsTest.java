package com.srinu.payments.payment.observability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BusinessStateMetricsTest {
 @Test void durableSnapshotIsBoundedAndFailureDoesNotLookHealthy() {
  JdbcTemplate jdbc=mock(JdbcTemplate.class); var registry=new SimpleMeterRegistry();var metrics=new BusinessStateMetrics(jdbc,registry);
  assertTrue(Double.isNaN(registry.get("poc.outbox.pending").gauge().value()));
  when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("status","COMPLETED","amount",4L),Map.of("status","UNTRUSTED","amount",9L)));
  when(jdbc.queryForMap(anyString())).thenReturn(Map.of("amount",2L,"oldest",System.currentTimeMillis()/1000.0-80));metrics.refresh();
  assertEquals(4,registry.get("poc.payment.records").tag("status","COMPLETED").gauge().value());
  assertEquals(0,registry.get("poc.payment.records").tag("status","FAILED").gauge().value());
  assertEquals(7,registry.find("poc.payment.records").gauges().size());assertEquals(2,registry.get("poc.outbox.pending").gauge().value());
  assertTrue(registry.get("poc.outbox.oldest.pending.seconds").gauge().value()>=80);
  double stamp=registry.get("poc.business.snapshot.last.success.timestamp.seconds").gauge().value();
  when(jdbc.queryForMap(anyString())).thenThrow(new IllegalStateException("synthetic secret"));metrics.refresh();
  assertEquals(0,registry.get("poc.business.snapshot.healthy").gauge().value());assertEquals(stamp,registry.get("poc.business.snapshot.last.success.timestamp.seconds").gauge().value());
  assertEquals(4,registry.get("poc.payment.records").tag("status","COMPLETED").gauge().value());
 }
}
