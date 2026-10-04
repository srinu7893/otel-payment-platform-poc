package com.srinu.payments.payment.outbox;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxTraceContextTest {
    @Test void persistsRequestContextAcrossSchedulerBoundary() {
        var parent = SpanContext.create("1234567890abcdef1234567890abcdef", "1234567890abcdef",
            TraceFlags.getSampled(), TraceState.builder().put("vendor", "value").build());
        OutboxEvent event;
        try (var scope = Context.root().with(Span.wrap(parent)).makeCurrent()) {
            event = new OutboxEvent("PAYMENT", UUID.randomUUID(), "PAYMENT_COMPLETED", "payment.completed", "{}", "test");
        }
        var restored = Span.fromContext(event.traceContext()).getSpanContext();
        assertThat(restored.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(restored.getSpanId()).isEqualTo(parent.getSpanId());
        assertThat(restored.getTraceState()).isEqualTo(parent.getTraceState());
        assertThat(restored.isRemote()).isTrue();
    }
    @Test void legacyOrMalformedContextDoesNotInheritSchedulerContext() {
        var scheduler = SpanContext.create("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb", TraceFlags.getSampled(), TraceState.getDefault());
        try (var scope = Context.root().with(Span.wrap(scheduler)).makeCurrent()) {
            assertThat(Span.fromContext(OutboxTraceContext.restore(null, null)).getSpanContext().isValid()).isFalse();
            assertThat(Span.fromContext(OutboxTraceContext.restore("bad", "bad")).getSpanContext().isValid()).isFalse();
        }
    }
}
