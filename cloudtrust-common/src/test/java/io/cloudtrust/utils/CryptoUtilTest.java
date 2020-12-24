package io.cloudtrust.utils;

import io.cloudtrust.crypto.CryptoUtil;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.hamcrest.MatcherAssert.assertThat;

public class CryptoUtilTest {

    @Rule
    public final EnvironmentVariables envVariables = new EnvironmentVariables();

    @Before
    public void init() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        envVariables.set("DB_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(key));
    }

    @Test
    public void testSecretEncryptionDecryption() {
        byte[] plainText = new byte[32]; //256 bits
        new SecureRandom().nextBytes(plainText);
        assertThat(CryptoUtil.decryptFromDatabaseStorage(CryptoUtil.encryptForDatabaseStorage(plainText)), equalTo(plainText));
    }

    /**
     * test that the output fits by default in a VARCHAR(255) field in the DB)
     */
    @Test
    public void testEncryptedSecretSize() {
        byte[] plainText = new byte[32]; //256 bits (key to be secured)
        new SecureRandom().nextBytes(plainText);
        String ciperStr = CryptoUtil.encryptForDatabaseStorage(plainText);
        assertThat(ciperStr.length(), lessThan(255));
    }

    @Test
    public void testGcmEncryptDecrypt() throws NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
        final String testString = "This is a test string to encrypt and decrypt!";
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey secretKey = keyGen.generateKey();
        String encryptedString = CryptoUtil.gcmEncrypt(secretKey, testString);
        String decryptedString = CryptoUtil.gcmDecrypt(secretKey, encryptedString);
        assertThat(testString, Matchers.is(decryptedString));
    }

}
