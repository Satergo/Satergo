package com.satergo.extra;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Encryption / Decryption service using the AES algorithm
 */
public class AESEncryption {

	public static byte[] encryptData(byte[] iv, SecretKey secretKey, byte[] data)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		// Encryption mode on
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

		// Encrypt the data
		byte[] encryptedData = cipher.doFinal(data);

		// Concatenate everything and return the final data
		byte[] concatenated = new byte[iv.length + encryptedData.length];
		System.arraycopy(iv, 0, concatenated, 0, iv.length);
		System.arraycopy(encryptedData, 0, concatenated, iv.length, encryptedData.length);
		return concatenated;
	}

	/**
	 * This method will encrypt the given data
	 *
	 * @param password password for encrypting
	 * @param data data to encrypt
	 * @return Encrypted data in a byte array
	 */
	public static byte[] encryptData(char[] password, byte[] data)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException, InvalidKeySpecException {

		byte[] iv = generateNonce12();

		// Prepare password
		SecretKey secretKey = generateSecretKey(password, iv);

		return encryptData(iv, secretKey, data);
	}

	public static byte[] decryptData(byte[] iv, SecretKey secretKey, ByteBuffer encryptedData)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {

		// Get the rest of encrypted data
		byte[] cipherBytes = new byte[encryptedData.remaining()];
		encryptedData.get(cipherBytes);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		// Decryption mode on
		cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

		// Decrypt the data
		return cipher.doFinal(cipherBytes);
	}

	public static byte[] decryptData(SecretKey secretKey, ByteBuffer encryptedData)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException {

		byte[] iv = new byte[12];
		encryptedData.get(iv);

		return decryptData(iv, secretKey, encryptedData);
	}

	public static byte[] decryptData(char[] password, byte[] iv, ByteBuffer encryptedData)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException, InvalidKeySpecException {

		// Prepare password
		SecretKey secretKey = generateSecretKey(password, iv);

		return decryptData(iv, secretKey, encryptedData);
	}

	public static byte[] decryptData(char[] password, ByteBuffer encryptedData)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
			BadPaddingException, IllegalBlockSizeException, InvalidKeySpecException {

		byte[] iv = new byte[12];
		encryptedData.get(iv);

		return decryptData(password, iv, encryptedData);
	}

	/**
	 * Generates a 128-bit key from the given password and iv
	 *
	 * @param password Password
	 * @param iv Initialization vector
	 * @return Secret key
	 */
	public static SecretKey generateSecretKey(char[] password, byte[] iv) throws NoSuchAlgorithmException, InvalidKeySpecException {
		KeySpec spec = new PBEKeySpec(password, iv, 65536, 128); // AES-128
		SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
		return new SecretKeySpec(key, "AES");
	}

	public static byte[] generateNonce12() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] iv = new byte[12];
		secureRandom.nextBytes(iv);
		return iv;
	}
}
