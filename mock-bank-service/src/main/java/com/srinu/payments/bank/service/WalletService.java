package com.srinu.payments.bank.service;

import com.srinu.payments.bank.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    public WalletService(AccountRepository accounts, JdbcTemplate jdbc) { this.accounts=accounts; this.jdbc=jdbc; }
    public record Balance(String accountNumber, String customerName, BigDecimal balance, String currency, boolean active) {}
    public record Recipient(String accountNumber, String customerName, boolean active) {}
    public record Deposit(UUID id, String accountNumber, BigDecimal amount, String status) {}
    public record Activity(UUID id, String type, String direction, BigDecimal amount, String currency, String status, Instant createdAt) {}
    @Transactional(readOnly=true) public Balance balance(String number) {
        var a=accounts.findById(number).orElseThrow(()->new NoSuchElementException("Account not found"));
        return new Balance(number,a.getCustomerName(),a.getBalance(),"INR",a.isActive());
    }
    @Transactional(readOnly=true) public Recipient recipient(String number) {
        var a=balance(number); return new Recipient(number,a.customerName(),a.active());
    }
    @Transactional public Deposit deposit(String number, UUID key, BigDecimal amount) {
        if (amount==null || amount.signum()<=0 || amount.scale()>2 || amount.compareTo(new BigDecimal("100000"))>0)
            throw new IllegalArgumentException("Deposit must be between INR 0.01 and 100000 with at most two decimal places");
        // Serialize changes to the balance and replay check together, including concurrent retries.
        var account=accounts.findForUpdate(number).orElseThrow(()->new NoSuchElementException("Account not found"));
        var prior=jdbc.query("select account_number, amount from bank.wallet_deposit where id=?",(rs,row)->new Deposit(key,rs.getString(1),rs.getBigDecimal(2),"COMPLETED"),key);
        if(!prior.isEmpty()) {
            var p=prior.get(0);
            if(!p.accountNumber().equals(number) || p.amount().compareTo(amount)!=0) throw new IllegalArgumentException("Deposit key was already used for a different request");
            return p;
        }
        account.credit(amount); accounts.save(account);
        jdbc.update("insert into bank.wallet_deposit(id, account_number, amount) values (?,?,?)",key,number,amount);
        return new Deposit(key,number,amount,"COMPLETED");
    }
    @Transactional(readOnly=true) public List<Activity> activity(String account, int page, int size) {
        int limit=Math.max(1,Math.min(size,100));
        return jdbc.query("""
            select * from (
                select transaction_id as id, 'PAYMENT' as kind, 'DEBIT' as direction, amount, 'INR' as currency, status, created_at from bank.debit_transactions where account_number=?
                union all select transaction_id, 'REFUND', 'CREDIT', amount, 'INR', status, created_at from bank.refund_transactions where account_number=?
                union all select id, 'DEPOSIT', 'CREDIT', amount, 'INR', 'COMPLETED', created_at from bank.wallet_deposit where account_number=?
                union all select id, 'TRANSFER', case when sender_account=? then 'DEBIT' else 'CREDIT' end, amount, currency, status, created_at from bank.bank_transaction where sender_account=? or receiver_account=?
            ) activity order by created_at desc, id limit ? offset ?
            """,(rs,row)->new Activity(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getString("direction"),rs.getBigDecimal("amount"),rs.getString("currency"),rs.getString("status"),rs.getTimestamp("created_at").toInstant()),
            account,account,account,account,account,account,limit,Math.max(0,page)*limit);
    }
}
