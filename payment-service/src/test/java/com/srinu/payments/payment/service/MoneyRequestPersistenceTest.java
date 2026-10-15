package com.srinu.payments.payment.service;
import com.srinu.payments.payment.domain.MoneyRequest;
import com.srinu.payments.payment.repository.MoneyRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class MoneyRequestPersistenceTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        if(Boolean.getBoolean("poc.native.integration")){
            properties.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:55432/poc_integration");
            properties.add("spring.datasource.username",()->"payments");properties.add("spring.datasource.password",()->"payments");
        } else {
            var pg=new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine");pg.start();
            properties.add("spring.datasource.url",pg::getJdbcUrl);properties.add("spring.datasource.username",pg::getUsername);properties.add("spring.datasource.password",pg::getPassword);
        }
    }
    @Autowired MoneyRequestRepository repo;
    @Test void migrationAndCompareAndSetPreventPayingDeclinedRequests(){
        var id=UUID.randomUUID();repo.saveAndFlush(new MoneyRequest(id,"owner","ACC1001","Demo","ACC2001",new BigDecimal("2.00"),"Test"));
        assertThat(repo.findByRequesterCustomerIdOrPayerAccountOrderByCreatedAtDesc("payer","ACC2001",PageRequest.of(0,20)).getContent()).extracting(MoneyRequest::getId).contains(id);
        assertThat(repo.transition(id,"OPEN","DECLINED")).isEqualTo(1);
        assertThat(repo.transition(id,"OPEN","PROCESSING")).isZero();
        assertThat(repo.findById(id).orElseThrow().getStatus()).isEqualTo("DECLINED");
    }
}
