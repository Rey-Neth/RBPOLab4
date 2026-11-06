package org.example.service;

import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.model.SurveyQuestion;
import org.example.repository.SurveyAnswerRepository;
import org.example.repository.SurveyQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SurveyQuestionService {
    private final SurveyQuestionRepository questionRepository;
    private final SurveyAnswerRepository answerRepository;
    private final SurveyService surveyService;

    public SurveyQuestionService(SurveyQuestionRepository questionRepository,
                                 SurveyAnswerRepository answerRepository, SurveyService surveyService) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.surveyService = surveyService;
    }

    public List<SurveyQuestion> findAll() {
        return questionRepository.findAll();
    }

    public SurveyQuestion findById(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SurveyQuestion not found with id: " + id));
    }

    public SurveyQuestion create(SurveyQuestion question) {
        if (question.getSurveyId() == null) {
            throw new BadRequestException("SurveyId is required");
        }
        surveyService.requireDraft(question.getSurveyId());
        validateOptions(question.getOptions());
        if (question.getText() == null || question.getText().isBlank()) {
            throw new BadRequestException("Text is required");
        }
        question.setId(null);
        if (question.isRequired() == null) {
            question.setRequired(false);
        }
        if (question.getOptions() == null) {
            question.setOptions(List.of());
        }
        return questionRepository.save(question);
    }

    public SurveyQuestion update(Long id, SurveyQuestion updates) {
        SurveyQuestion existing = findById(id);
        surveyService.requireDraft(existing.getSurveyId());
        validateOptions(updates.getOptions());
        if (updates.getSurveyId() != null) {
            surveyService.requireDraft(updates.getSurveyId());
            if (!updates.getSurveyId().equals(existing.getSurveyId())
                    && !answerRepository.findByQuestionId(id).isEmpty()) {
                throw new BadRequestException("Cannot move a question with answers to another survey");
            }
        }
        if (updates.getOptions() != null && !updates.getOptions().isEmpty()) {
            boolean invalidatesAnswers = answerRepository.findByQuestionId(id).stream()
                    .anyMatch(answer -> answer.getSelectedValue() != null
                            && !updates.getOptions().contains(answer.getSelectedValue()));
            if (invalidatesAnswers) {
                throw new BadRequestException("Options must allow existing answers");
            }
        }
        if (updates.getSurveyId() != null) {
            existing.setSurveyId(updates.getSurveyId());
        }
        if (updates.getText() != null && !updates.getText().isBlank()) {
            existing.setText(updates.getText());
        }
        if (updates.isRequired() != null) {
            existing.setRequired(updates.isRequired());
        }
        if (updates.getOptions() != null) {
            existing.setOptions(updates.getOptions());
        }
        return questionRepository.save(existing);
    }

    public void delete(Long id) {
        surveyService.requireDraft(findById(id).getSurveyId());
        questionRepository.deleteById(id);
    }

    private void validateOptions(List<String> options) {
        if (options != null && (options.stream().anyMatch(value -> value == null || value.isBlank())
                || options.stream().distinct().count() != options.size())) {
            throw new BadRequestException("Options must be nonempty and unique");
        }
    }
}
