package com.srinu.payments.payment.service;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.WalletClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service public class WalletService {
    private final CustomerClient customers;
    private final WalletClient bank;
    public WalletService(CustomerClient customers,WalletClient bank){this.customers=customers;this.bank=bank;}
    public CustomerClient.CustomerView customer(String id){
        var customer=customers.get(id);
        if(customer==null || !customer.active()) throw new IllegalStateException("Customer account is inactive");
        return customer;
    }
    public WalletClient.Balance balance(String customer){return bank.balance(customer(customer).accountNumber());}
    public WalletClient.Recipient recipient(String account){
        if(account==null || !account.matches("[A-Za-z0-9-]{3,64}")) throw new IllegalArgumentException("Enter a valid account number");
        var recipient=bank.recipient(account);
        if(recipient==null || !recipient.active()) throw new IllegalArgumentException("Recipient account is inactive");
        return recipient;
    }
    public WalletClient.Deposit deposit(String customer,WalletClient.DepositRequest request){return bank.deposit(customer(customer).accountNumber(),request);}
    public List<WalletClient.Activity> activity(String customer,int page,int size){return bank.activity(customer(customer).accountNumber(),Math.max(0,page),Math.max(1,Math.min(size,100)));}
}
