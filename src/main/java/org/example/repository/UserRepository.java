package org.example.repository;

import org.example.model.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Override
    @Query("select e from User e order by e.id")
    List<User> findAll();

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from app_users where id = :id", nativeQuery = true)
    void deleteById(@Param("id") Long id);

    Optional<User> findByUsername(String username);
}
