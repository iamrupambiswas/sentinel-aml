package com.sentinel.aml.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;

@Converter
@Configuration
public class PIIAttributeConverter implements AttributeConverter<String, String> {

    private static final String AES = "AES";
    private final Key key;

    // Spring Boot 3 automatically injects this value into the Hibernate converter
    public PIIAttributeConverter(@Value("${sentinel.encryption.key}") String secret) {
        byte[] decodedKey = Base64.getDecoder().decode(secret);
        this.key = new SecretKeySpec(decodedKey, AES);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt PII data", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt PII data", e);
        }
    }
}