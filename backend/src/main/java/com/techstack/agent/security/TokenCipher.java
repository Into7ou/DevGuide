package com.techstack.agent.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final BytesEncryptor encryptor;

    public TokenCipher(@Value("${app.security.token-cipher-key:}") String key) {
        // 安全默认值：不允许空密钥或公开的占位密钥，漏配时 fail-fast 而不是静默用弱密钥。
        if (key == null || key.isBlank() || "dev-insecure-key".equals(key)) {
            throw new IllegalStateException(
                    "TOKEN_CIPHER_KEY 未配置或仍为不安全默认值，请在 .env 中显式设置 app.security.token-cipher-key");
        }
        // Encryptors.standard 的 salt 参数会被 Hex.decode() 解码，必须是偶数长度的十六进制字符串，
        // 因此把固定的盐值 "techstack-agent" 转成 hex 再传入。
        String saltHex = new String(Hex.encode("techstack-agent".getBytes(StandardCharsets.UTF_8)));
        this.encryptor = Encryptors.standard(key, saltHex);
    }

    public String encrypt(String plain) {
        return ENCODER.encodeToString(encryptor.encrypt(plain.getBytes(StandardCharsets.UTF_8)));
    }

    public String decrypt(String cipher) {
        return new String(encryptor.decrypt(DECODER.decode(cipher)), StandardCharsets.UTF_8);
    }
}
