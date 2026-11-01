package dev.agenticwallet.spike.backend.decisioning.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DecisionJpaRepository extends JpaRepository<DecisionEntity, UUID> {}
