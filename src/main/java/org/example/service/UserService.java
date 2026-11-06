package org.example.service;

import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AccessService access;

    public UserService(UserRepository users, PasswordEncoder encoder, AccessService access) {
        this.users = users;
        this.encoder = encoder;
        this.access = access;
    }

    public List<User> findAll() {
        return users.findAll();
    }

    public User findById(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    public User register(User user) {
        // Поля из запроса не позволяют получить права администратора.
        user.setRole("USER");
        user.setEnabled(true);
        return create(user);
    }

    public User create(User user) {
        user.setUsername(normalizeUsername(user.getUsername()));
        if (user.getRole() == null) user.setRole("USER");
        validateRole(user.getRole());
        if (user.getEnabled() == null) user.setEnabled(true);
        user.setPasswordHash(encodePassword(user.getPassword()));
        user.setPassword(null);
        user.setId(null);
        user.setCreatedAt(LocalDateTime.now());
        return users.save(user);
    }

    public User update(Long id, User updates) {
        User existing = findById(id);
        if (!access.isAdmin() && (updates.getRole() != null || updates.getEnabled() != null)) {
            throw new AccessDeniedException("Только администратор меняет роль и состояние учётной записи");
        }
        if (updates.getUsername() != null) existing.setUsername(normalizeUsername(updates.getUsername()));
        if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
        if (updates.getRole() != null) {
            validateRole(updates.getRole());
            existing.setRole(updates.getRole());
        }
        if (updates.getEnabled() != null) existing.setEnabled(updates.getEnabled());
        if (updates.getPassword() != null) existing.setPasswordHash(encodePassword(updates.getPassword()));
        return users.save(existing);
    }

    public void delete(Long id) {
        findById(id);
        users.deleteById(id);
    }

    private String normalizeUsername(String username) {
        if (username == null || !username.matches("[a-zA-Z0-9_]{3,40}")) {
            throw new BadRequestException("Логин: 3–40 латинских букв, цифр или _");
        }
        return username.toLowerCase(Locale.ROOT);
    }

    private String encodePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s).*[a-z].*") || !password.matches("(?s).*[A-Z].*")
                || !password.matches("(?s).*[0-9].*") || !password.matches("(?s).*[^a-zA-Z0-9].*")) {
            throw new BadRequestException("Пароль: от 12 символов, до 72 байт; нужны большая и маленькая латинские буквы, цифра и символ");
        }
        return encoder.encode(password);
    }

    private void validateRole(String role) {
        if (!List.of("USER", "ADMIN").contains(role)) {
            throw new BadRequestException("Допустимые роли: USER и ADMIN");
        }
    }
}
