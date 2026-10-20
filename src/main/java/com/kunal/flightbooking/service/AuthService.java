package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.Role;
import com.kunal.flightbooking.domain.User;
import com.kunal.flightbooking.dto.request.LoginRequest;
import com.kunal.flightbooking.dto.request.RegisterRequest;
import com.kunal.flightbooking.dto.response.AuthResponse;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.repository.UserRepository;
import com.kunal.flightbooking.security.AuthUser;
import com.kunal.flightbooking.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /** Self-registration always yields the USER role; ADMINs are provisioned out-of-band. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = userRepository.save(new User(req.fullName().trim(), email,
                passwordEncoder.encode(req.password()), req.phone(), Role.USER));
        return tokenFor(AuthUser.from(user));
    }

    public AuthResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                req.email().trim().toLowerCase(Locale.ROOT), req.password()));
        return tokenFor((AuthUser) auth.getPrincipal());
    }

    private AuthResponse tokenFor(AuthUser user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.expirationSeconds(),
                user.getId(), user.getUsername(), user.getRole().name());
    }
}
