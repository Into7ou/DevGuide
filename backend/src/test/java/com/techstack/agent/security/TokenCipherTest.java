package com.techstack.agent.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.Encryptors;

class TokenCipherTest {

    private static final String STRONG_KEY = "0123456789abcdef0123456789abcdef";
    private static final String NEXT_KEY = "abcdef0123456789abcdef0123456789";

    @Test
    void rejectsShortOrPlaceholderKeys() {
        assertThrows(IllegalStateException.class, () -> new TokenCipher("short"));
        assertThrows(IllegalStateException.class, () -> new TokenCipher("change_me_cipher_key_change_me"));
    }

    @Test
    void newCiphertextsAreVersionedAndRoundTrip() {
        TokenCipher cipher = new TokenCipher(STRONG_KEY);

        String encrypted = cipher.encrypt("github-token");

        assertTrue(encrypted.startsWith("v2:"));
        assertEquals("github-token", cipher.decrypt(encrypted));
    }

    @Test
    void previousKeyCanDecryptVersionedCiphertextDuringRotation() {
        String encryptedWithOldKey = new TokenCipher(STRONG_KEY).encrypt("github-token");

        TokenCipher rotated = new TokenCipher(NEXT_KEY, STRONG_KEY);

        assertEquals("github-token", rotated.decrypt(encryptedWithOldKey));
    }

    @Test
    void legacyUnversionedCiphertextRemainsReadable() {
        String salt = new String(Hex.encode("techstack-agent".getBytes(StandardCharsets.UTF_8)));
        String legacy = Base64.getEncoder().encodeToString(
                Encryptors.standard(STRONG_KEY, salt).encrypt("github-token".getBytes(StandardCharsets.UTF_8)));

        assertEquals("github-token", new TokenCipher(NEXT_KEY, STRONG_KEY).decrypt(legacy));
    }
}
