package com.srinu.payments.notification.observability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeliveryStateMetricsTest {
 @Test void emptyDeliveryIsUnknownAndFailedRefreshRetainsLastSnapshot() {
  JdbcTemplate jdbc=mock(JdbcTemplate.class);var registry=new SimpleMeterRegistry();var metrics=new DeliveryStateMetrics(jdbc,registry);
  when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("status","SENT","amount",3L)));
  var ages=new HashMap<String,Object>();ages.put("oldest",0);ages.put("mean",null);when(jdbc.queryForMap(anyString())).thenReturn(ages);metrics.refresh();
  assertTrue(Double.isNaN(registry.get("poc.notification.mean.received.to.sent.seconds").gauge().value()));
  assertEquals(0,registry.get("poc.notification.oldest.unsent.seconds").gauge().value());
  assertEquals(3,registry.get("poc.notification.records").tag("status","SENT").gauge().value());
  ages.put("mean",2.5);metrics.refresh();assertEquals(2.5,registry.get("poc.notification.mean.received.to.sent.seconds").gauge().value());
  when(jdbc.queryForList(anyString())).thenThrow(new IllegalStateException());metrics.refresh();
  assertEquals(0,registry.get("poc.business.snapshot.healthy").gauge().value());
  assertEquals(3,registry.get("poc.notification.records").tag("status","SENT").gauge().value());
 }
}
