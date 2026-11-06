package org.example.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Entity
@Table(name = "survey_participations")
public class SurveyParticipation {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", insertable = false, updatable = false)
    private Survey survey;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "respondent_id", insertable = false, updatable = false)
    private User respondent;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "survey_id")
    private Long surveyId;
    @Column(name = "respondent_id")
    private Long respondentId;
    @Column(length = 32)
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;

    public SurveyParticipation() {}


    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getSurveyId() { return surveyId; }

    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }

    public Long getRespondentId() { return respondentId; }

    public void setRespondentId(Long respondentId) { this.respondentId = respondentId; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
