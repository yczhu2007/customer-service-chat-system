package com.example.customerservice.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;


/**
 * 密码工具类。
 *
 * 使用PBKDF2WithHmacSHA256进行密码哈希。
 *
 * password字段保存格式：
 *
 * pbkdf2$迭代次数$salt$hash
 */
public final class PasswordUtil {

    private static final String PREFIX =
            "pbkdf2";


    private static final String ALGORITHM =
            "PBKDF2WithHmacSHA256";


    private static final int ITERATIONS =
            120_000;


    private static final int KEY_LENGTH =
            256;


    private static final int SALT_LENGTH =
            16;


    private static final SecureRandom
            SECURE_RANDOM =
            new SecureRandom();


    private PasswordUtil() {
    }


    /**
     * 将明文密码转换成可保存到数据库的字符串。
     */
    public static String hash(
            String rawPassword
    ) {

        validatePassword(
                rawPassword
        );


        byte[] salt =
                new byte[SALT_LENGTH];


        SECURE_RANDOM.nextBytes(
                salt
        );


        byte[] passwordHash =
                calculateHash(
                        rawPassword,
                        salt,
                        ITERATIONS
                );


        return PREFIX
                + "$"
                + ITERATIONS
                + "$"
                + Base64.getEncoder()
                .encodeToString(
                        salt
                )
                + "$"
                + Base64.getEncoder()
                .encodeToString(
                        passwordHash
                );
    }


    /** 校验用户输入的密码，只接受 PBKDF2 格式的数据库密码。 */
    public static boolean matches(
            String rawPassword,
            String storedPassword
    ) {

        if (
                rawPassword == null ||
                        storedPassword == null
        ) {

            return false;
        }


        if (!storedPassword.startsWith(PREFIX + "$")) {
            return false;
        }
        return matchesHashedPassword(rawPassword, storedPassword);
    }


    private static boolean matchesHashedPassword(
            String rawPassword,
            String storedPassword
    ) {

        try {

            String[] parts =
                    storedPassword.split(
                            "\\$"
                    );


            if (parts.length != 4) {

                return false;
            }


            int iterations =
                    Integer.parseInt(
                            parts[1]
                    );


            byte[] salt =
                    Base64.getDecoder()
                            .decode(
                                    parts[2]
                            );


            byte[] expectedHash =
                    Base64.getDecoder()
                            .decode(
                                    parts[3]
                            );


            byte[] actualHash =
                    calculateHash(
                            rawPassword,
                            salt,
                            iterations
                    );


            return MessageDigest.isEqual(
                    expectedHash,
                    actualHash
            );

        } catch (Exception e) {

            return false;
        }
    }


    private static byte[] calculateHash(
            String rawPassword,
            byte[] salt,
            int iterations
    ) {

        PBEKeySpec specification =
                new PBEKeySpec(

                        rawPassword.toCharArray(),

                        salt,

                        iterations,

                        KEY_LENGTH
                );


        try {

            SecretKeyFactory factory =
                    SecretKeyFactory
                            .getInstance(
                                    ALGORITHM
                            );


            return factory
                    .generateSecret(
                            specification
                    )
                    .getEncoded();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "密码哈希计算失败",
                    e
            );

        } finally {

            specification.clearPassword();
        }
    }


    private static void validatePassword(
            String rawPassword
    ) {

        if (
                rawPassword == null ||
                        rawPassword.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "密码不能为空"
            );
        }

        if (rawPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "密码长度不能少于8个字符"
            );
        }
    }
}
