package at.ac.tgm.submit.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TOKEN_LIFETIME_SECONDS = 28_800L;
    private static final int SECRET_LENGTH = 32;

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public JwtService(ObjectMapper objectMapper, @Value("${JWT_SECRET:}") String configuredSecret) {
        this.objectMapper = objectMapper;
        this.secret = configuredSecret.isBlank() ? generateSecret() : configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes.");
        }
    }

    public String issueToken(AuthenticatedUser user) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(objectMapper.createObjectNode()
                    .put("alg", "HS256").put("typ", "JWT")));
            long expiresAt = Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond();
            var payloadClaims = objectMapper.createObjectNode()
                    .put("sub", user.id().toString()).put("username", user.username());
            payloadClaims.set("roles", objectMapper.valueToTree(user.roles()));
            payloadClaims.put("exp", expiresAt);
            String payload = encode(objectMapper.writeValueAsBytes(payloadClaims));
            String unsigned = header + "." + payload;
            return unsigned + "." + encode(sign(unsigned));
        } catch (JsonProcessingException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to issue authentication token.", exception);
        }
    }

    public AuthenticatedUser parseToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            String unsigned = parts[0] + "." + parts[1];
            byte[] expected = sign(unsigned);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }
            JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (claims.path("exp").asLong() <= Instant.now().getEpochSecond()) {
                return null;
            }
            List<Role> roles = parseRoles(claims.path("roles"));
            if (roles == null) {
                return null;
            }
            return new AuthenticatedUser(UUID.fromString(claims.path("sub").asText()),
                    claims.path("username").asText(), roles);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            return null;
        }
    }

    private List<Role> parseRoles(JsonNode rolesClaim) {
        if (!rolesClaim.isArray() || rolesClaim.isEmpty()) {
            return null;
        }
        List<Role> roles = new java.util.ArrayList<>();
        for (JsonNode roleClaim : rolesClaim) {
            if (!roleClaim.isTextual()) {
                return null;
            }
            roles.add(Role.valueOf(roleClaim.asText()));
        }
        return roles;
    }

    private byte[] sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
