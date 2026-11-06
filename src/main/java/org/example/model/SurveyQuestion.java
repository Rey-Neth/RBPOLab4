package org.example.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

@Entity
@Table(name = "survey_questions")
public class SurveyQuestion {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", insertable = false, updatable = false)
    private Survey survey;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "survey_id")
    private Long surveyId;
    @Column(length = 2000)
    private String text;
    private Boolean required;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "survey_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_order")
    @Column(name = "option_value", length = 1000, nullable = false)
    private List<String> options;

    public SurveyQuestion() {}

    public SurveyQuestion(Long id, Long surveyId, String text, Boolean required, List<String> options) {
        this.id = id;
        this.surveyId = surveyId;
        this.text = text;
        this.required = required;
        this.options = options == null ? null : new java.util.ArrayList<>(options);
    }

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getSurveyId() { return surveyId; }

    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }

    public String getText() { return text; }

    public void setText(String text) { this.text = text; }

    public Boolean isRequired() { return required; }

    public void setRequired(Boolean required) { this.required = required; }

    public List<String> getOptions() { return options; }

    public void setOptions(List<String> options) { this.options = options == null ? null : new java.util.ArrayList<>(options); }
}
