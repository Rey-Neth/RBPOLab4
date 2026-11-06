package org.example.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "survey_answers")
public class SurveyAnswer {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participation_id", insertable = false, updatable = false)
    private SurveyParticipation participation;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private SurveyQuestion question;

    @JsonIgnore
    @Column(name = "survey_id")
    private Long surveyId;

    @JsonIgnore
    public Long getSurveyId() { return surveyId; }
    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "participation_id")
    private Long participationId;
    @Column(name = "question_id")
    private Long questionId;
    @Column(length = 1000)
    private String selectedValue;

    public SurveyAnswer() {}


    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getParticipationId() { return participationId; }

    public void setParticipationId(Long participationId) { this.participationId = participationId; }

    public Long getQuestionId() { return questionId; }

    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getSelectedValue() { return selectedValue; }

    public void setSelectedValue(String selectedValue) { this.selectedValue = selectedValue; }
}
