package org.example.service;

import org.example.model.*;
import org.example.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AccessService {
    private final UserRepository users;
    private final SurveyRepository surveys;
    private final SurveyQuestionRepository questions;
    private final SurveyParticipationRepository participations;
    private final SurveyAnswerRepository answers;

    public AccessService(UserRepository users, SurveyRepository surveys, SurveyQuestionRepository questions,
                         SurveyParticipationRepository participations, SurveyAnswerRepository answers) {
        this.users = users;
        this.surveys = surveys;
        this.questions = questions;
        this.participations = participations;
        this.answers = answers;
    }

    public boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : users.findByUsername(auth.getName()).map(User::getId).orElse(null);
    }

    private boolean isSelf(Long id) {
        return id != null && Objects.equals(id, currentUserId());
    }

    public boolean canManageUser(Long id) {
        return isAdmin() || isSelf(id);
    }

    public boolean canReadSurvey(Long id) {
        return isAdmin() || (id != null && surveys.findById(id)
                .map(s -> isSelf(s.getAuthorId()) || !"DRAFT".equals(s.getStatus())).orElse(false));
    }

    public boolean canManageSurvey(Long id) {
        return isAdmin() || (id != null && surveys.findById(id).map(s -> isSelf(s.getAuthorId())).orElse(false));
    }

    public boolean canReadQuestion(Long id) {
        return isAdmin() || (id != null && questions.findById(id).map(q -> canReadSurvey(q.getSurveyId())).orElse(false));
    }

    public boolean canManageQuestion(Long id) {
        return isAdmin() || (id != null && questions.findById(id).map(q -> canManageSurvey(q.getSurveyId())).orElse(false));
    }

    public boolean canReadParticipation(Long id) {
        return isAdmin() || (id != null && participations.findById(id)
                .map(p -> isSelf(p.getRespondentId()) || canManageSurvey(p.getSurveyId())).orElse(false));
    }

    public boolean canManageParticipation(Long id) {
        return isAdmin() || (id != null && participations.findById(id).map(p -> isSelf(p.getRespondentId())).orElse(false));
    }

    public boolean canReadAnswer(Long id) {
        return isAdmin() || (id != null && answers.findById(id).map(a -> canReadParticipation(a.getParticipationId())).orElse(false));
    }

    public boolean canManageAnswer(Long id) {
        return isAdmin() || (id != null && answers.findById(id).map(a -> canManageParticipation(a.getParticipationId())).orElse(false));
    }
}
