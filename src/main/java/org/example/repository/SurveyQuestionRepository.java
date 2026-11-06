package org.example.repository;

import org.example.model.SurveyQuestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    @Override
    @Query("select e from SurveyQuestion e order by e.id")
    List<SurveyQuestion> findAll();

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from survey_questions where id = :id", nativeQuery = true)
    void deleteById(@Param("id") Long id);

    @Query("select e from SurveyQuestion e where e.surveyId = :id order by e.id")
    List<SurveyQuestion> findBySurveyId(@Param("id") Long id);
}
