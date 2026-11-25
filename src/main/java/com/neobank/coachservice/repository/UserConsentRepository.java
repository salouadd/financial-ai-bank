package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    Optional<UserConsent> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}