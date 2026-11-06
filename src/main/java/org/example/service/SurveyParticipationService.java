package org.example.service;

import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.model.SurveyParticipation;
import org.example.repository.SurveyAnswerRepository;
import org.example.repository.SurveyParticipationRepository;
import org.example.repository.UserRepository;
import org.example.repository.SurveyQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class SurveyParticipationService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final SurveyParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final SurveyAnswerRepository answerRepository;
    private final SurveyService surveyService;
    private final SurveyQuestionRepository questionRepository;

    public SurveyParticipationService(SurveyParticipationRepository participationRepository,
                                      UserRepository userRepository,
                                      SurveyAnswerRepository answerRepository, SurveyService surveyService,
                                      SurveyQuestionRepository questionRepository) {
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.answerRepository = answerRepository;
        this.surveyService = surveyService;
        this.questionRepository = questionRepository;
    }

    public List<SurveyParticipation> findAll() {
        return participationRepository.findAll();
    }

    public SurveyParticipation findById(Long id) {
        return participationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SurveyParticipation not found with id: " + id));
    }

    public SurveyParticipation create(SurveyParticipation participation) {
        if (participation.getSurveyId() == null || participation.getRespondentId() == null) {
            throw new BadRequestException("SurveyId and respondentId are required");
        }
        surveyService.requireActive(participation.getSurveyId());
        if (!userRepository.existsById(participation.getRespondentId())) {
            throw new BadRequestException("Respondent does not exist");
        }
        if (participation.getStatus() != null && !"IN_PROGRESS".equals(participation.getStatus())) {
            throw new BadRequestException("New participation must be IN_PROGRESS");
        }
        participation.setId(null);
        participation.setStatus("IN_PROGRESS");
        participation.setCreatedAt(LocalDateTime.now());
        participation.setSubmittedAt(null);
        return participationRepository.save(participation);
    }

    public SurveyParticipation update(Long id, SurveyParticipation updates) {
        SurveyParticipation existing = lockEditable(id);
        if (updates.getSurveyId() != null && !updates.getSurveyId().equals(existing.getSurveyId())) {
            throw new BadRequestException("Participation cannot be moved to another survey");
        }
        if (updates.getStatus() != null && !updates.getStatus().equals(existing.getStatus())) {
            throw new BadRequestException("Use submit or restart to change participation status");
        }
        if (updates.getRespondentId() != null) {
            if (!userRepository.existsById(updates.getRespondentId())) {
                throw new BadRequestException("Respondent does not exist");
            }
            existing.setRespondentId(updates.getRespondentId());
        }
        return participationRepository.save(existing);
    }

    public SurveyParticipation lockEditable(Long id) {
        SurveyParticipation participation = lock(id);
        if (!"IN_PROGRESS".equals(participation.getStatus())) {
            throw new BadRequestException("Only an unfinished participation can be edited");
        }
        return participation;
    }

    public SurveyParticipation submit(Long id) {
        SurveyParticipation participation = lockEditable(id);
        var answers = answerRepository.findByParticipationId(id);
        var questions = questionRepository.findBySurveyId(participation.getSurveyId());
        for (var question : questions) {
            if (Boolean.TRUE.equals(question.isRequired()) && answers.stream()
                    .noneMatch(answer -> answer.getQuestionId().equals(question.getId()))) {
                throw new BadRequestException("Answer all required questions before submitting");
            }
        }
        participation.setStatus("SUBMITTED");
        participation.setSubmittedAt(LocalDateTime.now());
        return participationRepository.save(participation);
    }

    public SurveyParticipation restart(Long id) {
        SurveyParticipation participation = lock(id);
        if (!"SUBMITTED".equals(participation.getStatus())) {
            throw new BadRequestException("Only submitted participation can be restarted");
        }
        answerRepository.deleteByParticipationId(id);
        participation.setStatus("IN_PROGRESS");
        participation.setSubmittedAt(null);
        return participationRepository.save(participation);
    }

    public void delete(Long id) {
        surveyService.lock(findById(id).getSurveyId());
        participationRepository.deleteById(id);
    }

    private SurveyParticipation lock(Long id) {
        SurveyParticipation participation = findById(id);
        surveyService.requireActive(participation.getSurveyId());
        // После ожидания блокировки перечитываем состояние участия.
        entityManager.refresh(participation);
        return participationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Participation not found"));
    }
}
