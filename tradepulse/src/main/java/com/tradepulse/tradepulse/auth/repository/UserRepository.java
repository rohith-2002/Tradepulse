package com.tradepulse.tradepulse.auth.repository;

import com.tradepulse.tradepulse.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(Long id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
