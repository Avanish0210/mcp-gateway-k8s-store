package com.mcp_gateway_k8s_store.backend.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {
    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret:}") String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank())
            throw new IllegalStateException("JWT_SECRET must be configured as a Base64-encoded secret of at least 32 bytes");
        final byte[] key;
        try { key = Base64.getDecoder().decode(encodedSecret); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET must be valid Base64", e); }
        if (key.length < 32) throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
