package com.flightlog.user;

import com.flightlog.security.Role;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapAdminConfiguration {
    @Bean
    ApplicationRunner createBootstrapAdmin(UserAccountRepository users, PasswordEncoder encoder,
            @Value("${DADB_BOOTSTRAPADMIN_USERNAME:}") String username,
            @Value("${DADB_BOOTSTRAPADMIN_PASSWORD:}") String password) {
        return args -> {
            if (!username.isBlank() && !password.isBlank() && users.findByUsername(username).isEmpty()) {
                users.save(new UserAccount(UUID.randomUUID(), username, "Administrator",
                        encoder.encode(password), Role.ADMIN));
            }
        };
    }
}
