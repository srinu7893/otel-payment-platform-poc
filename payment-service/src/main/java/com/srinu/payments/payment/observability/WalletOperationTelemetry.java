package com.srinu.payments.payment.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** Attempts and processing latency, never unique money movement or amounts. */
@Aspect
@Component
public class WalletOperationTelemetry {
    private final MeterRegistry registry;
    private static final Set<String> METHODS=Set.of("balance","recipient","deposit","activity","create","list","act");
    public WalletOperationTelemetry(MeterRegistry registry){this.registry=registry;}
    @Around("execution(public * com.srinu.payments.payment.service.WalletService.*(..)) || execution(public * com.srinu.payments.payment.service.MoneyRequestService.*(..))")
    public Object observe(ProceedingJoinPoint call) throws Throwable {
        String method=call.getSignature().getName();
        if(!METHODS.contains(method))return call.proceed();
        String operation=(call.getSignature().getDeclaringTypeName().endsWith("MoneyRequestService")?"request.":"wallet.")+method;
        var span=GlobalOpenTelemetry.getTracer("payment-poc.wallet").spanBuilder(operation).startSpan();
        var timer=Timer.start(registry);String outcome="success";
        try(var scope=span.makeCurrent()){
            return call.proceed();
        }catch(Throwable error){
            outcome=error instanceof IllegalArgumentException || error instanceof org.springframework.security.access.AccessDeniedException ? "rejected":"error";
            span.setStatus(StatusCode.ERROR,"Wallet operation failed");
            throw error;
        }finally{
            span.setAttribute("poc.operation",operation);span.setAttribute("poc.outcome",outcome);span.end();
            registry.counter("poc.wallet.operation.attempts","operation",operation,"outcome",outcome).increment();
            timer.stop(Timer.builder("poc.wallet.operation.duration").tags("operation",operation,"outcome",outcome)
                .description("Operation processing time; includes retries as separate attempts").publishPercentileHistogram().register(registry));
        }
    }
}
