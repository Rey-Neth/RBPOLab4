package org.example.controller;

import org.example.model.*;
import org.example.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final UserService users;
    private final SurveyService surveys;
    private final SurveyQuestionService questions;
    private final SurveyParticipationService participations;
    private final SurveyAnswerService answers;
    private final AccessService access;

    public ApiController(UserService users, SurveyService surveys, SurveyQuestionService questions,
                         SurveyParticipationService participations, SurveyAnswerService answers, AccessService access) {
        this.users = users;
        this.surveys = surveys;
        this.questions = questions;
        this.participations = participations;
        this.answers = answers;
        this.access = access;
    }

    // Вход и регистрация.
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/auth/register") @ResponseStatus(CREATED)
    public User register(@RequestBody User user) { return users.register(user); }

    @GetMapping("/auth/me")
    public User me() { return users.findById(access.currentUserId()); }

    // Пользователи: списком, созданием и удалением управляет ADMIN.
    @GetMapping("/users") @PreAuthorize("hasRole('ADMIN')")
    public List<User> users() { return users.findAll(); }

    @GetMapping("/users/{id}") @PreAuthorize("@accessService.canManageUser(#id)")
    public User user(@PathVariable Long id) { return users.findById(id); }

    @PostMapping("/users") @ResponseStatus(CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(@RequestBody User user) { return users.create(user); }

    @PatchMapping("/users/{id}") @PreAuthorize("@accessService.canManageUser(#id)")
    public User updateUser(@PathVariable Long id, @RequestBody User updates) { return users.update(id, updates); }

    @DeleteMapping("/users/{id}") @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(@PathVariable Long id) { users.delete(id); }

    // Опросы: USER изменяет свои, ADMIN — любые.
    @GetMapping("/surveys")
    public List<Survey> surveys() {
        return surveys.findAll().stream().filter(s -> access.canReadSurvey(s.getId())).toList();
    }

    @GetMapping("/surveys/{id}") @PreAuthorize("@accessService.canReadSurvey(#id)")
    public Survey survey(@PathVariable Long id) { return surveys.findById(id); }

    @PostMapping("/surveys") @ResponseStatus(CREATED)
    @PreAuthorize("@accessService.canManageUser(#survey.authorId)")
    public Survey createSurvey(@RequestBody Survey survey) { return surveys.create(survey); }

    @PatchMapping("/surveys/{id}")
    @PreAuthorize("@accessService.canManageSurvey(#id) and (#updates.authorId == null or @accessService.canManageUser(#updates.authorId))")
    public Survey updateSurvey(@PathVariable Long id, @RequestBody Survey updates) { return surveys.update(id, updates); }

    @DeleteMapping("/surveys/{id}") @ResponseStatus(NO_CONTENT)
    @PreAuthorize("@accessService.canManageSurvey(#id)")
    public void deleteSurvey(@PathVariable Long id) { surveys.delete(id); }

    @PostMapping("/surveys/{id}/publish") @PreAuthorize("@accessService.canManageSurvey(#id)")
    public Survey publish(@PathVariable Long id) { return surveys.publish(id); }

    @PostMapping("/surveys/{id}/close") @PreAuthorize("@accessService.canManageSurvey(#id)")
    public Survey close(@PathVariable Long id) { return surveys.close(id); }

    @PostMapping("/surveys/{id}/copy") @ResponseStatus(CREATED)
    @PreAuthorize("@accessService.canManageSurvey(#id)")
    public Survey copy(@PathVariable Long id) { return surveys.copy(id); }

    // Вопросы принадлежат опросу.
    @GetMapping("/survey-questions")
    public List<SurveyQuestion> questions() {
        return questions.findAll().stream().filter(q -> access.canReadQuestion(q.getId())).toList();
    }

    @GetMapping("/survey-questions/{id}") @PreAuthorize("@accessService.canReadQuestion(#id)")
    public SurveyQuestion question(@PathVariable Long id) { return questions.findById(id); }

    @PostMapping("/survey-questions") @ResponseStatus(CREATED)
    @PreAuthorize("@accessService.canManageSurvey(#question.surveyId)")
    public SurveyQuestion createQuestion(@RequestBody SurveyQuestion question) { return questions.create(question); }

    @PatchMapping("/survey-questions/{id}")
    @PreAuthorize("@accessService.canManageQuestion(#id) and (#updates.surveyId == null or @accessService.canManageSurvey(#updates.surveyId))")
    public SurveyQuestion updateQuestion(@PathVariable Long id, @RequestBody SurveyQuestion updates) { return questions.update(id, updates); }

    @DeleteMapping("/survey-questions/{id}") @ResponseStatus(NO_CONTENT)
    @PreAuthorize("@accessService.canManageQuestion(#id)")
    public void deleteQuestion(@PathVariable Long id) { questions.delete(id); }

    // Участия: менять и отправлять может респондент или ADMIN.
    @GetMapping("/survey-participations")
    public List<SurveyParticipation> participations() {
        return participations.findAll().stream().filter(p -> access.canReadParticipation(p.getId())).toList();
    }

    @GetMapping("/survey-participations/{id}") @PreAuthorize("@accessService.canReadParticipation(#id)")
    public SurveyParticipation participation(@PathVariable Long id) { return participations.findById(id); }

    @PostMapping("/survey-participations") @ResponseStatus(CREATED)
    @PreAuthorize("@accessService.canManageUser(#participation.respondentId)")
    public SurveyParticipation createParticipation(@RequestBody SurveyParticipation participation) { return participations.create(participation); }

    @PatchMapping("/survey-participations/{id}")
    @PreAuthorize("@accessService.canManageParticipation(#id) and (#updates.respondentId == null or @accessService.canManageUser(#updates.respondentId))")
    public SurveyParticipation updateParticipation(@PathVariable Long id, @RequestBody SurveyParticipation updates) { return participations.update(id, updates); }

    @DeleteMapping("/survey-participations/{id}") @ResponseStatus(NO_CONTENT)
    @PreAuthorize("@accessService.canManageParticipation(#id)")
    public void deleteParticipation(@PathVariable Long id) { participations.delete(id); }

    @PostMapping("/survey-participations/{id}/submit") @PreAuthorize("@accessService.canManageParticipation(#id)")
    public SurveyParticipation submit(@PathVariable Long id) { return participations.submit(id); }

    @PostMapping("/survey-participations/{id}/restart") @PreAuthorize("@accessService.canManageParticipation(#id)")
    public SurveyParticipation restart(@PathVariable Long id) { return participations.restart(id); }

    // Ответы проверяются через владельца участия.
    @GetMapping("/survey-answers")
    public List<SurveyAnswer> answers() {
        return answers.findAll().stream().filter(a -> access.canReadAnswer(a.getId())).toList();
    }

    @GetMapping("/survey-answers/{id}") @PreAuthorize("@accessService.canReadAnswer(#id)")
    public SurveyAnswer answer(@PathVariable Long id) { return answers.findById(id); }

    @PostMapping("/survey-answers") @ResponseStatus(CREATED)
    @PreAuthorize("@accessService.canManageParticipation(#answer.participationId)")
    public SurveyAnswer createAnswer(@RequestBody SurveyAnswer answer) { return answers.create(answer); }

    @PatchMapping("/survey-answers/{id}") @PreAuthorize("@accessService.canManageAnswer(#id)")
    public SurveyAnswer updateAnswer(@PathVariable Long id, @RequestBody SurveyAnswer updates) { return answers.update(id, updates); }

    @DeleteMapping("/survey-answers/{id}") @ResponseStatus(NO_CONTENT)
    @PreAuthorize("@accessService.canManageAnswer(#id)")
    public void deleteAnswer(@PathVariable Long id) { answers.delete(id); }
}
