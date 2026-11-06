package org.example.service;

import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.model.Survey;
import org.example.repository.SurveyParticipationRepository;
import org.example.repository.SurveyQuestionRepository;
import org.example.repository.SurveyRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class SurveyService {
    private final SurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final SurveyQuestionRepository questionRepository;
    private final SurveyParticipationRepository participationRepository;

    public SurveyService(SurveyRepository surveyRepository,
                         UserRepository userRepository,
                         SurveyQuestionRepository questionRepository,
                         SurveyParticipationRepository participationRepository) {
        this.surveyRepository = surveyRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.participationRepository = participationRepository;
    }

    public List<Survey> findAll() {
        return surveyRepository.findAll();
    }

    public Survey findById(Long id) {
        return surveyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Survey not found with id: " + id));
    }

    public Survey create(Survey survey) {
        if (survey.getStatus() != null && !"DRAFT".equals(survey.getStatus())) {
            throw new BadRequestException("A new survey must be a draft");
        }
        validateDates(survey);
        if (survey.getTitle() == null || survey.getTitle().isBlank()) {
            throw new BadRequestException("Title is required");
        }
        if (survey.getAuthorId() == null) {
            throw new BadRequestException("AuthorId is required");
        }
        if (!userRepository.existsById(survey.getAuthorId())) {
            throw new BadRequestException("Author with id " + survey.getAuthorId() + " does not exist");
        }
        survey.setId(null);
        LocalDateTime now = LocalDateTime.now();
        survey.setCreatedAt(now);
        survey.setUpdatedAt(now);
        survey.setStatus("DRAFT");
        return surveyRepository.save(survey);
    }

    public Survey update(Long id, Survey updates) {
        Survey existing = requireDraft(id);
        if (updates.getStatus() != null && !updates.getStatus().equals(existing.getStatus())) {
            throw new BadRequestException("Use publish or close to change survey status");
        }
        if (updates.getTitle() != null && !updates.getTitle().isBlank()) {
            existing.setTitle(updates.getTitle());
        }
        if (updates.getAuthorId() != null) {
            if (!userRepository.existsById(updates.getAuthorId())) {
                throw new BadRequestException("Author with id " + updates.getAuthorId() + " does not exist");
            }
            existing.setAuthorId(updates.getAuthorId());
        }
        if (updates.getStartDate() != null) {
            existing.setStartDate(updates.getStartDate());
        }
        if (updates.getEndDate() != null) {
            existing.setEndDate(updates.getEndDate());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        validateDates(existing);
        return surveyRepository.save(existing);
    }

    public void delete(Long id) {
        lock(id);
        // Связанные записи удаляет PostgreSQL: ON DELETE CASCADE.
        surveyRepository.deleteById(id);
    }

    public Survey lock(Long id) {
        return surveyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Survey not found with id: " + id));
    }

    public Survey requireDraft(Long id) {
        Survey survey = lock(id);
        if (!"DRAFT".equals(survey.getStatus())) {
            throw new BadRequestException("Only a draft survey can be edited");
        }
        return survey;
    }

    public Survey requireActive(Long id) {
        Survey survey = lock(id);
        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(survey.getStatus()) || now.isBefore(survey.getStartDate())
                || !now.isBefore(survey.getEndDate())) {
            throw new BadRequestException("Survey is not accepting responses now");
        }
        return survey;
    }

    public Survey publish(Long id) {
        Survey survey = requireDraft(id);
        if (questionRepository.findBySurveyId(id).isEmpty()) {
            throw new BadRequestException("Add at least one question before publishing");
        }
        LocalDateTime now = LocalDateTime.now();
        if (survey.getStartDate() == null) {
            survey.setStartDate(now);
        }
        if (survey.getEndDate() == null || !survey.getEndDate().isAfter(now)) {
            throw new BadRequestException("Set a future endDate before publishing");
        }
        validateDates(survey);
        survey.setStatus("ACTIVE");
        survey.setUpdatedAt(now);
        return surveyRepository.save(survey);
    }

    public Survey close(Long id) {
        Survey survey = lock(id);
        if (!"ACTIVE".equals(survey.getStatus())) {
            throw new BadRequestException("Only an active survey can be closed");
        }
        for (var participation : participationRepository.findBySurveyId(id)) {
            if ("IN_PROGRESS".equals(participation.getStatus())) {
                participation.setStatus("CANCELED");
                participation.setSubmittedAt(null);
                participationRepository.save(participation);
            }
        }
        survey.setStatus("CLOSED");
        survey.setUpdatedAt(LocalDateTime.now());
        return surveyRepository.save(survey);
    }

    public Survey copy(Long id) {
        Survey source = lock(id);
        if (!"CLOSED".equals(source.getStatus())) {
            throw new BadRequestException("Only a closed survey can be used for a new round");
        }
        LocalDateTime now = LocalDateTime.now();
        String title = source.getTitle();
        if (title.length() > 245) {
            title = title.substring(0, 245);
        }
        Survey copy = new Survey(null, title + " (copy)", source.getAuthorId(), null, null, "DRAFT", now, now);
        surveyRepository.save(copy);
        for (var question : questionRepository.findBySurveyId(id)) {
            questionRepository.save(new org.example.model.SurveyQuestion(null, copy.getId(), question.getText(),
                    question.isRequired(), question.getOptions()));
        }
        return copy;
    }

    private void validateDates(Survey survey) {
        if (survey.getStartDate() != null && survey.getEndDate() != null
                && !survey.getEndDate().isAfter(survey.getStartDate())) {
            throw new BadRequestException("endDate must be after startDate");
        }
    }
}
