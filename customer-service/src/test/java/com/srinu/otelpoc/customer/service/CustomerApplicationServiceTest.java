package com.srinu.otelpoc.customer.service;

import com.srinu.otelpoc.customer.domain.Customer;
import com.srinu.otelpoc.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerApplicationServiceTest {
    @Test
    void updatesExistingCustomer() {
        var repo = mock(CustomerRepository.class);
        var customer = new Customer("c1", "Old", "old@example.com", "ACC1001");
        when(repo.findById("c1")).thenReturn(Optional.of(customer));
        var service = new CustomerApplicationService(repo);
        var updated = service.update("c1", "New", "new@example.com", "ACC1002");
        assertEquals("New", updated.getName());
        assertEquals("new@example.com", updated.getEmail());
        assertEquals("ACC1002", updated.getAccountNumber());
    }
}
