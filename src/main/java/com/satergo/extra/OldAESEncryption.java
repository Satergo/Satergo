package com.satergo.extra;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Old encryption used for formatVersion 1 and 0.
 */
public class OldAESEncryption implements Encryption {

	public static final OldAESEncryption INSTANCE = new OldAESEncryption();

	private OldAESEncryption() {}

	@Override
	public byte[] encryptData(byte[] iv, SecretKey secretKey, byte[] data)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

		// Encrypt the data
		return cipher.doFinal(data);
	}

	@Override
	public byte[] decryptData(byte[] iv, SecretKey secretKey, byte[] encryptedData)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		// Decryption mode on
		cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

		// Decrypt the data
		return cipher.doFinal(encryptedData);
	}

	/**
	 * Generates a 128-bit key from the given password and salt
	 */
	@Override
	public SecretKey generateSecretKey(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
		KeySpec spec = new PBEKeySpec(password, salt, 65536, 128);
		SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
		return new SecretKeySpec(key, "AES");
	}
}
