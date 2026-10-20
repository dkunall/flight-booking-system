package com.kunal.flightbooking.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.domain.Role;
import com.kunal.flightbooking.domain.User;
import com.kunal.flightbooking.repository.UserRepository;
import com.kunal.flightbooking.security.AuthUser;
import com.kunal.flightbooking.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application context (H2 in PostgreSQL mode + Flyway) exercised through MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDataFactory.class)
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TestDataFactory data;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    /** Registers a fresh USER through the public API and returns its bearer token. */
    protected String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"fullName":"Kunal Test","email":"%s","password":"S3cure-pass!","phone":"9876543210"}
                """.formatted(email);
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + read(result).get("accessToken").asText();
    }

    /** ADMINs cannot self-register, so tests provision one directly and mint its token. */
    protected String adminToken() {
        User admin = userRepository.save(new User("Admin", "admin-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(UUID.randomUUID().toString()), null, Role.ADMIN));
        return "Bearer " + jwtService.generateToken(AuthUser.from(admin));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static String newIdempotencyKey() {
        return "key-" + UUID.randomUUID();
    }
}
