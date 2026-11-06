package org.example.repository;

import org.example.model.SurveyParticipation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface SurveyParticipationRepository extends JpaRepository<SurveyParticipation, Long> {
    @Override
    @Query("select e from SurveyParticipation e order by e.id")
    List<SurveyParticipation> findAll();

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from survey_participations where id = :id", nativeQuery = true)
    void deleteById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SurveyParticipation e where e.id = :id")
    Optional<SurveyParticipation> findByIdForUpdate(@Param("id") Long id);

    @Query("select e from SurveyParticipation e where e.surveyId = :id order by e.id")
    List<SurveyParticipation> findBySurveyId(@Param("id") Long id);
}
