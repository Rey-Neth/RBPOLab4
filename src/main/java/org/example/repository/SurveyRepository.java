package org.example.repository;

import org.example.model.Survey;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    @Override
    @Query("select e from Survey e order by e.id")
    List<Survey> findAll();

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from surveys where id = :id", nativeQuery = true)
    void deleteById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Survey e where e.id = :id")
    Optional<Survey> findByIdForUpdate(@Param("id") Long id);
}
