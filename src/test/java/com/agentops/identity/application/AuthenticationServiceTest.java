package com.agentops.identity.application;

import com.agentops.identity.api.LoginRequest;
import com.agentops.identity.config.IdentityProperties;
import com.agentops.identity.domain.UserAccount;
import com.agentops.identity.domain.UserStatus;
import com.agentops.identity.infrastructure.persistence.UserAccountRepository;
import com.agentops.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authenticationService = new AuthenticationService(
                userAccountRepository,
                passwordEncoder,
                new PrincipalFactory(),
                jwtService,
                new IdentityProperties("default")
        );
    }

    @Test
    void shouldAuthenticateAgainstBcryptHash() {
        UserAccount account = accountWithPassword("correct-password");
        when(userAccountRepository.findByTenantIdAndUsernameIgnoreCase("default", "admin"))
                .thenReturn(Optional.of(account));
        when(jwtService.issueAccessToken(any())).thenReturn(new JwtService.IssuedToken(
                "token-value",
                Instant.parse("2030-01-01T00:15:00Z"),
                900
        ));

        var response = authenticationService.login(new LoginRequest(null, "Admin", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void shouldRejectIncorrectPasswordWithoutIssuingToken() {
        UserAccount account = accountWithPassword("correct-password");
        when(userAccountRepository.findByTenantIdAndUsernameIgnoreCase("default", "admin"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest(null, "admin", "incorrect-password")
        )).isInstanceOf(BadCredentialsException.class);
    }

    private UserAccount accountWithPassword(String rawPassword) {
        return new UserAccount(
                UUID.randomUUID(),
                "default",
                "admin",
                passwordEncoder.encode(rawPassword),
                "Admin",
                "admin@agentops.local",
                UserStatus.ACTIVE
        );
    }
}
