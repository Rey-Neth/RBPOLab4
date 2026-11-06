package org.example.repository;

import org.example.model.SurveyAnswer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    @Override
    @Query("select e from SurveyAnswer e order by e.id")
    List<SurveyAnswer> findAll();

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from survey_answers where id = :id", nativeQuery = true)
    void deleteById(@Param("id") Long id);

    @Query("select e from SurveyAnswer e where e.participationId = :id order by e.id")
    List<SurveyAnswer> findByParticipationId(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from survey_answers where participation_id = :id", nativeQuery = true)
    void deleteByParticipationId(@Param("id") Long id);

    @Query("select e from SurveyAnswer e where e.questionId = :id order by e.id")
    List<SurveyAnswer> findByQuestionId(@Param("id") Long id);
}
