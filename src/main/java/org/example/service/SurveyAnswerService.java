package org.example.service;

import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.model.SurveyAnswer;
import org.example.model.SurveyQuestion;
import org.example.model.SurveyParticipation;
import org.example.repository.SurveyAnswerRepository;
import org.example.repository.SurveyQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SurveyAnswerService {
    private final SurveyAnswerRepository answerRepository;
    private final SurveyQuestionRepository questionRepository;
    private final SurveyParticipationService participationService;

    public SurveyAnswerService(SurveyAnswerRepository answerRepository,
                               SurveyQuestionRepository questionRepository, SurveyParticipationService participationService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.participationService = participationService;
    }

    public List<SurveyAnswer> findAll() {
        return answerRepository.findAll();
    }

    public SurveyAnswer findById(Long id) {
        return answerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SurveyAnswer not found with id: " + id));
    }

    public SurveyAnswer create(SurveyAnswer answer) {
        SurveyParticipation participation = validate(answer.getParticipationId(), answer.getQuestionId(), answer.getSelectedValue());
        answer.setId(null);
        answer.setSurveyId(participation.getSurveyId());
        return answerRepository.save(answer);
    }

    public SurveyAnswer update(Long id, SurveyAnswer updates) {
        SurveyAnswer existing = findById(id);
        if (updates.getParticipationId() != null
                && !updates.getParticipationId().equals(existing.getParticipationId())) {
            throw new BadRequestException("An answer cannot be moved to another participation");
        }
        Long participationId = updates.getParticipationId() != null
                ? updates.getParticipationId() : existing.getParticipationId();
        Long questionId = updates.getQuestionId() != null
                ? updates.getQuestionId() : existing.getQuestionId();
        String selectedValue = updates.getSelectedValue() != null
                ? updates.getSelectedValue() : existing.getSelectedValue();
        validate(participationId, questionId, selectedValue);
        existing.setParticipationId(participationId);
        existing.setQuestionId(questionId);
        existing.setSelectedValue(selectedValue);
        return answerRepository.save(existing);
    }

    private SurveyParticipation validate(Long participationId, Long questionId, String selectedValue) {
        if (participationId == null) {
            throw new BadRequestException("ParticipationId is required");
        }
        SurveyParticipation participation = participationService.lockEditable(participationId);
        if (questionId == null) {
            throw new BadRequestException("QuestionId is required");
        }
        SurveyQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BadRequestException("Question with id " + questionId + " does not exist"));
        if (!participation.getSurveyId().equals(question.getSurveyId())) {
            throw new BadRequestException("Question and participation must belong to the same survey");
        }
        if (selectedValue == null || selectedValue.isBlank()) {
            throw new BadRequestException("Selected value is required");
        }
        if (question.getOptions() != null && !question.getOptions().isEmpty()
                && !question.getOptions().contains(selectedValue)) {
            throw new BadRequestException("Selected value is not in allowed options");
        }
        return participation;
    }

    public void delete(Long id) {
        participationService.lockEditable(findById(id).getParticipationId());
        answerRepository.deleteById(id);
    }
}
