package org.example;

import jakarta.servlet.DispatcherType;
import org.example.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import java.util.Locale;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService accounts(UserRepository users) {
        return login -> {
            var user = users.findByUsername(login.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(user.getUsername()).password(user.getPasswordHash())
                    .roles(user.getRole()).disabled(!user.getEnabled()).build();
        };
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .cors(cors -> cors.configurationSource(request -> new CorsConfiguration()))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .build();
    }

}
