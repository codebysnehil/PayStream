package com.paystream.ledger.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
}
