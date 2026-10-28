package com.paystream.ledger.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Row-locks one account by primary key (UPDLOCK/HOLDLOCK on SQL Server). It must stay a
     * point lookup: a multi-row IN/ORDER BY lock query can be planned as a scan, and a serializable
     * scan takes key-range locks that serialise every settlement, even between unrelated accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateByAccountNumber(String accountNumber);
}
