package com.srinu.payments.payment.service;

import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.repository.PaymentRepository;
import com.srinu.payments.payment.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class RiskPolicyService {
    private static final Logger log = LoggerFactory.getLogger(RiskPolicyService.class);
    private static final List<PaymentStatus> PAYMENT_EXPOSURE_STATUSES = List.of(
        PaymentStatus.PROCESSING, PaymentStatus.RECONCILIATION_REQUIRED, PaymentStatus.COMPLETED);
    private static final List<String> TRANSFER_EXPOSURE_STATUSES = List.of(
        "PROCESSING", "RECONCILIATION_REQUIRED", "COMPLETED");

    private final PaymentRepository payments;
    private final TransferRepository transfers;
    private final BigDecimal maxTransactionAmount;
    private final BigDecimal maxDailyCustomerAmount;
    private final BigDecimal maxDailyAccountAmount;

    public RiskPolicyService(PaymentRepository payments,
                             TransferRepository transfers,
                             @Value("${risk.max-transaction-amount:100000.00}") BigDecimal maxTransactionAmount,
                             @Value("${risk.max-daily-customer-amount:200000.00}") BigDecimal maxDailyCustomerAmount,
                             @Value("${risk.max-daily-account-amount:200000.00}") BigDecimal maxDailyAccountAmount) {
        this.payments = payments;
        this.transfers = transfers;
        this.maxTransactionAmount = positive(maxTransactionAmount, "maxTransactionAmount");
        this.maxDailyCustomerAmount = positive(maxDailyCustomerAmount, "maxDailyCustomerAmount");
        this.maxDailyAccountAmount = positive(maxDailyAccountAmount, "maxDailyAccountAmount");
    }

    public void validate(String customerId, String accountNumber, BigDecimal proposedAmount) {
        if (proposedAmount.compareTo(maxTransactionAmount) > 0) {
            reject("MAX_TRANSACTION_AMOUNT", customerId, accountNumber, proposedAmount,
                "Transaction amount exceeds configured single-transaction limit");
        }

        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal paymentCustomer = zero(payments.sumExposureByCustomerSince(
            customerId, dayStart, PAYMENT_EXPOSURE_STATUSES));
        BigDecimal transferCustomer = zero(transfers.sumExposureByCustomerSince(
            customerId, dayStart, TRANSFER_EXPOSURE_STATUSES));
        BigDecimal customerExposure = paymentCustomer.add(transferCustomer);

        if (customerExposure.add(proposedAmount).compareTo(maxDailyCustomerAmount) > 0) {
            reject("MAX_DAILY_CUSTOMER_AMOUNT", customerId, accountNumber, proposedAmount,
                "Customer daily transaction exposure limit would be exceeded");
        }

        BigDecimal paymentAccount = zero(payments.sumExposureByAccountSince(
            accountNumber, dayStart, PAYMENT_EXPOSURE_STATUSES));
        BigDecimal transferAccount = zero(transfers.sumExposureBySenderSince(
            accountNumber, dayStart, TRANSFER_EXPOSURE_STATUSES));
        BigDecimal accountExposure = paymentAccount.add(transferAccount);

        if (accountExposure.add(proposedAmount).compareTo(maxDailyAccountAmount) > 0) {
            reject("MAX_DAILY_ACCOUNT_AMOUNT", customerId, accountNumber, proposedAmount,
                "Account daily transaction exposure limit would be exceeded");
        }

        log.info("event=RISK_CHECK_PASSED customerId={} account={} amount={} customerExposure={} accountExposure={}",
            customerId, mask(accountNumber), proposedAmount, customerExposure, accountExposure);
    }

    private void reject(String rule, String customerId, String accountNumber,
                        BigDecimal amount, String message) {
        log.warn("event=RISK_CHECK_REJECTED rule={} customerId={} account={} amount={}",
            rule, customerId, mask(accountNumber), amount);
        throw new RiskLimitExceededException(rule, message);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
