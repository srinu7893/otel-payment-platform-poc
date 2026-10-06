package com.srinu.payments.payment.outbox;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashMap;
import java.util.Map;

/** Persist only W3C trace context, never arbitrary baggage or credentials. */
final class OutboxTraceContext {
    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        public Iterable<String> keys(Map<String, String> carrier) { return carrier.keySet(); }
        public String get(Map<String, String> carrier, String key) { return carrier == null ? null : carrier.get(key); }
    };
    static Map<String, String> capture() {
        Map<String, String> carrier = new HashMap<>();
        PROPAGATOR.inject(Context.current(), carrier, Map::put);
        return carrier;
    }
    static Context restore(String traceparent, String tracestate) {
        Map<String, String> carrier = new HashMap<>();
        if (traceparent != null) carrier.put("traceparent", traceparent);
        if (tracestate != null) carrier.put("tracestate", tracestate);
        // Legacy events without context start a new trace, not the relay scheduler's trace.
        return PROPAGATOR.extract(Context.root(), carrier, GETTER);
    }
    private OutboxTraceContext() {}
}
