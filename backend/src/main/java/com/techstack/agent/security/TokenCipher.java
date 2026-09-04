package com.techstack.agent.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Component;

/**
 * 对称加密工具：加密/解密用户的 OAuth access token，避免明文落库。
 * 密钥来自 .env（app.security.token-cipher-key），不硬编码。
 */
@Component
public class TokenCipher {

    private static final String V2_PREFIX = "v2:";
    private static final int MIN_CURRENT_KEY_LENGTH = 32;
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final List<KeyEncryptors> keys;

    public TokenCipher(@Value("${app.security.token-cipher-key:}") String key) {
        this(key, "");
    }

    @Autowired
    public TokenCipher(@Value("${app.security.token-cipher-key:}") String key,
                       @Value("${app.security.token-cipher-previous-keys:}") String previousKeys) {
        validateCurrentKey(key);
        List<String> configuredKeys = new ArrayList<>();
        configuredKeys.add(key);
        if (previousKeys != null && !previousKeys.isBlank()) {
            for (String previous : previousKeys.split(",")) {
                String candidate = previous.trim();
                if (!candidate.isEmpty()) {
                    configuredKeys.add(candidate);
                }
            }
        }
        String saltHex = new String(Hex.encode("techstack-agent".getBytes(StandardCharsets.UTF_8)));
        this.keys = configuredKeys.stream()
                .map(candidate -> new KeyEncryptors(
                        Encryptors.stronger(candidate, saltHex),
                        Encryptors.standard(candidate, saltHex)))
                .toList();
    }

    public String encrypt(String plain) {
        byte[] encrypted = keys.getFirst().stronger().encrypt(plain.getBytes(StandardCharsets.UTF_8));
        return V2_PREFIX + ENCODER.encodeToString(encrypted);
    }

    public String decrypt(String cipher) {
        boolean v2 = cipher != null && cipher.startsWith(V2_PREFIX);
        String payload = v2 ? cipher.substring(V2_PREFIX.length()) : cipher;
        RuntimeException lastFailure = null;
        for (KeyEncryptors candidate : keys) {
            try {
                BytesEncryptor decryptor = v2 ? candidate.stronger() : candidate.legacy();
                return new String(decryptor.decrypt(DECODER.decode(payload)), StandardCharsets.UTF_8);
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        throw new IllegalArgumentException("无法使用当前或历史密钥解密用户 Token", lastFailure);
    }

    private static void validateCurrentKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase();
        boolean placeholder = normalized.contains("change_me")
                || normalized.contains("insecure")
                || normalized.contains("placeholder")
                || normalized.startsWith("your_");
        if (key == null || key.length() < MIN_CURRENT_KEY_LENGTH || placeholder) {
            throw new IllegalStateException(
                    "TOKEN_CIPHER_KEY 必须是至少 32 个字符的随机密钥，且不能使用示例占位值");
        }
    }

    private record KeyEncryptors(BytesEncryptor stronger, BytesEncryptor legacy) {
    }
}
