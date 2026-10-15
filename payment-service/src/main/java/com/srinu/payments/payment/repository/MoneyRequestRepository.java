package com.srinu.payments.payment.repository;
import com.srinu.payments.payment.domain.MoneyRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

public interface MoneyRequestRepository extends JpaRepository<MoneyRequest,UUID> {
    Page<MoneyRequest> findByRequesterCustomerIdOrPayerAccountOrderByCreatedAtDesc(String customer,String account,Pageable pageable);
    @Transactional @Modifying(clearAutomatically=true)
    @Query("update MoneyRequest r set r.status=:next where r.id=:id and r.status=:previous")
    int transition(@Param("id") UUID id,@Param("previous") String previous,@Param("next") String next);
    @Transactional @Modifying(clearAutomatically=true)
    @Query("update MoneyRequest r set r.status=:status,r.transferId=:transfer where r.id=:id and r.status='PROCESSING'")
    int finish(@Param("id") UUID id,@Param("status") String status,@Param("transfer") UUID transfer);
}
