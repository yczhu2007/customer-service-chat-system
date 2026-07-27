package com.example.customerservice.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import java.nio.charset.StandardCharsets;
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


    /**
     * 校验用户输入的密码。
     *
     * 同时暂时兼容数据库里已有的明文密码。
     * 兼容逻辑只用于旧数据迁移。
     */
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


        /*
         * 新格式密码：
         * pbkdf2$iterations$salt$hash
         */
        if (
                storedPassword.startsWith(
                        PREFIX + "$"
                )
        ) {

            return matchesHashedPassword(
                    rawPassword,
                    storedPassword
            );
        }


        /*
         * 兼容现有数据库中的明文密码。
         *
         * 用户第一次登录成功后，
         * ChatController会把它升级成哈希密码。
         */
        return MessageDigest.isEqual(

                rawPassword.getBytes(
                        StandardCharsets.UTF_8
                ),

                storedPassword.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }


    /**
     * 判断数据库密码是否需要升级。
     */
    public static boolean needsUpgrade(
            String storedPassword
    ) {

        return storedPassword == null ||
                !storedPassword.startsWith(
                        PREFIX + "$"
                );
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
    }
}