package com.srinu.payments.payment.client;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

class CustomerColdStartTest {
    @Test void customerStartupLongerThanOldFiveSecondTimeoutSucceedsAndPreservesIdentityInterceptor() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> workload=new AtomicReference<>();
        server.createContext("/api/v1/customers/demo",exchange->{
            workload.set(exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"));
            try{Thread.sleep(5500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] body="{\"id\":\"demo\",\"name\":\"Demo\",\"accountNumber\":\"ACC1001\",\"active\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try{
            var builder=RestClient.builder().requestInterceptor((request,body,chain)->{request.getHeaders().set("X-Serverless-Authorization","Bearer test-workload");return chain.execute(request,body);});
            var client=new CustomerClient(builder,"http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofSeconds(25));
            assertThat(client.get("demo").accountNumber()).isEqualTo("ACC1001");
            assertThat(workload.get()).isEqualTo("Bearer test-workload");
        }finally{server.stop(0);}
    }
    @Test void configuredReadTimeoutRemainsBounded() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/customers/demo",exchange->{try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}exchange.close();});server.start();
        try{
            var client=new CustomerClient(RestClient.builder(),"http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofMillis(100));
            assertThatThrownBy(()->client.get("demo")).isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        }finally{server.stop(0);}
    }
}
