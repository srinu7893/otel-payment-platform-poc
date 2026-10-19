package com.srinu.payments.payment.observability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class WalletOperationTelemetryTest {
    @Test void recordsBoundedAttemptsWithoutChangingResultsOrLeakingExceptions() throws Throwable {
        var meters=new SimpleMeterRegistry();var telemetry=new WalletOperationTelemetry(meters);
        var call=mock(ProceedingJoinPoint.class);var signature=mock(Signature.class);
        when(call.getSignature()).thenReturn(signature);when(signature.getName()).thenReturn("deposit");
        when(signature.getDeclaringTypeName()).thenReturn("com.srinu.payments.payment.service.WalletService");
        Object result=new Object();when(call.proceed()).thenReturn(result);
        assertSame(result,telemetry.observe(call));assertSame(result,telemetry.observe(call));
        assertEquals(2,meters.get("poc.wallet.operation.attempts").tags("operation","wallet.deposit","outcome","success").counter().count());
        var duration=meters.get("poc.wallet.operation.duration").tags("operation","wallet.deposit","outcome","success").timer();
        assertEquals(2,duration.count());
        assertTrue(duration.takeSnapshot().histogramCounts().length>0);
        var error=new IllegalArgumentException("SECRET-account-123");when(call.proceed()).thenThrow(error);
        assertSame(error,assertThrows(IllegalArgumentException.class,()->telemetry.observe(call)));
        assertEquals(1,meters.get("poc.wallet.operation.attempts").tags("operation","wallet.deposit","outcome","rejected").counter().count());
        assertTrue(meters.getMeters().stream().noneMatch(m->m.getId().toString().contains("SECRET")));
    }
}
