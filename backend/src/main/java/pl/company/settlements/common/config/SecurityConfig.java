package pl.company.settlements.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfException;
import pl.company.settlements.common.security.LoginAuthenticationProvider;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.CurrentUserResponse;
import pl.company.settlements.user.service.AppUserDetailsService;
import pl.company.settlements.user.service.UserAccessService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public AuthenticationManager authenticationManager(
            AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider dao =
                new DaoAuthenticationProvider(userDetailsService);

        dao.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(
                List.of(
                        new LoginAuthenticationProvider(dao)
                )
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            UserAccessService userAccessService
    ) throws Exception {
        http
                .authenticationManager(authenticationManager)

                .csrf(Customizer.withDefaults())

                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                        .sessionFixation(fixation ->
                                fixation.changeSessionId()
                        )
                )

                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers("/api/**")
                        .access(userAccessService::authorize)
                        .anyRequest().permitAll()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(
                                (request, response, authentication) ->
                                        writeJson(
                                                response,
                                                HttpStatus.OK.value(),
                                                CurrentUserResponse.from(
                                                        (AppUserPrincipal)
                                                                authentication
                                                                        .getPrincipal()
                                                )
                                        )
                        )
                        .failureHandler(
                                (request, response, exception) -> {
                                    new SecurityContextLogoutHandler()
                                            .logout(
                                                    request,
                                                    response,
                                                    null
                                            );

                                    if (exception instanceof LockedException) {
                                        response.setHeader(
                                                HttpHeaders.RETRY_AFTER,
                                                String.valueOf(
                                                        LoginAuthenticationProvider
                                                                .RETRY_AFTER_SECONDS
                                                )
                                        );

                                        writeError(
                                                request,
                                                response,
                                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                                "LOGIN_RATE_LIMIT",
                                                "Zbyt wiele nieudanych prób. "
                                                        + "Spróbuj ponownie za 10 minut."
                                        );
                                    } else {
                                        writeError(
                                                request,
                                                response,
                                                HttpStatus.UNAUTHORIZED.value(),
                                                "INVALID_CREDENTIALS",
                                                "Nieprawidłowy login lub hasło"
                                        );
                                    }
                                }
                        )
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(
                                (request, response, authentication) ->
                                        response.setStatus(
                                                HttpStatus.NO_CONTENT.value()
                                        )
                        )
                        .permitAll()
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        writeError(
                                                request,
                                                response,
                                                HttpStatus.UNAUTHORIZED.value(),
                                                "UNAUTHENTICATED",
                                                "Zaloguj się, aby kontynuować"
                                        )
                        )
                        .accessDeniedHandler(
                                (request, response, exception) -> {
                                    String code = "FORBIDDEN";
                                    String message =
                                            "Brak uprawnień do tej operacji";

                                    if (exception instanceof CsrfException) {
                                        code = "CSRF_INVALID";
                                        message =
                                                "Token CSRF jest nieaktualny "
                                                        + "lub nie został przesłany";
                                    } else if (
                                            UserAccessService
                                                    .PASSWORD_CHANGE_REQUIRED
                                                    .equals(
                                                            exception.getMessage()
                                                    )
                                    ) {
                                        code = UserAccessService
                                                .PASSWORD_CHANGE_REQUIRED;
                                        message =
                                                "Przed dostępem do danych "
                                                        + "zmień hasło tymczasowe";
                                    }

                                    writeError(
                                            request,
                                            response,
                                            HttpStatus.FORBIDDEN.value(),
                                            code,
                                            message
                                    );
                                }
                        )
                );

        return http.build();
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        writeJson(
                response,
                status,
                Map.of(
                        "status", status,
                        "code", code,
                        "message", message,
                        "path", request.getRequestURI()
                )
        );
    }

    private void writeJson(
            HttpServletResponse response,
            int status,
            Object body
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store"
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                body
        );
    }
}