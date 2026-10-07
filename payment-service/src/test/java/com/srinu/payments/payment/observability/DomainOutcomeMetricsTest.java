package com.srinu.payments.payment.observability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DomainOutcomeMetricsTest {
 @Test void boundsLabelsAndRetainsSnapshotOnQueryFailure(){
  var jdbc=mock(JdbcTemplate.class);var registry=new SimpleMeterRegistry();var metrics=new DomainOutcomeMetrics(jdbc,registry);
  when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("status","COMPLETED","amount",4),Map.of("status","untrusted","amount",99)));
  when(jdbc.queryForMap(anyString())).thenAnswer(call->call.getArgument(0,String.class).contains("percentile_cont")?Map.of("amount",3,"p50",1.0,"p95",4.0,"p99",5.0):Map.of("amount",5,"completed",4));
  metrics.refresh();verify(jdbc).setQueryTimeout(3);
  assertEquals(21,registry.find("poc.domain.records").gauges().size());
  assertEquals(4,registry.get("poc.domain.records").tag("operation","transfer").tag("status","COMPLETED").gauge().value());
  assertEquals(5,registry.get("poc.domain.cohort.records").tag("operation","refund").gauge().value());
  assertEquals(4,registry.get("poc.event.delivery.seconds").tag("quantile","0.95").gauge().value());
  double stamp=registry.get("poc.domain.snapshot.last.success.timestamp.seconds").gauge().value();
  when(jdbc.queryForList(anyString())).thenThrow(new IllegalStateException());metrics.refresh();
  assertEquals(0,registry.get("poc.domain.snapshot.healthy").gauge().value());
  assertEquals(stamp,registry.get("poc.domain.snapshot.last.success.timestamp.seconds").gauge().value());
  assertEquals(4,registry.get("poc.event.delivery.seconds").tag("quantile","0.95").gauge().value());
 }
}
