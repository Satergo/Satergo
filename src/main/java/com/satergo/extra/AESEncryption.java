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
 * example from {@link "https://nullbeans.com/how-to-encrypt-decrypt-files-byte-arrays-in-java-using-aes-gcm/"}
 */
public class AESEncryption {

	/**
	 * This method will encrypt the given data
	 *
	 * @param password password for encrypting
	 * @param data data to encrypt
	 * @return Encrypted data in a byte array
	 */
	public static byte[] encryptData(char[] password, byte[] data) throws NoSuchPaddingException,
			NoSuchAlgorithmException,
			InvalidAlgorithmParameterException,
			InvalidKeyException,
			BadPaddingException,
			IllegalBlockSizeException, InvalidKeySpecException {

		// Prepare the nonce
		SecureRandom secureRandom = new SecureRandom();

		// Nonce should be 12 bytes
		byte[] iv = new byte[12];
		secureRandom.nextBytes(iv);

		// Prepare your password
		SecretKey secretKey = generateSecretKey(password, iv);


		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		// Encryption mode on!
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

		// Encrypt the data
		byte[] encryptedData = cipher.doFinal(data);

		// Concatenate everything and return the final data
		ByteBuffer byteBuffer = ByteBuffer.allocate(4 + iv.length + encryptedData.length);
		byteBuffer.putInt(iv.length);
		byteBuffer.put(iv);
		byteBuffer.put(encryptedData);
		return byteBuffer.array();
	}


	public static byte[] decryptData(char[] password, byte[] encryptedData)
			throws NoSuchPaddingException,
			NoSuchAlgorithmException,
			InvalidAlgorithmParameterException,
			InvalidKeyException,
			BadPaddingException,
			IllegalBlockSizeException,
			InvalidKeySpecException {

		// Wrap the data into a byte buffer to ease the reading process
		ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);

		int noonceSize = byteBuffer.getInt();

		// Make sure that the file was encrypted properly
		if (noonceSize < 12 || noonceSize >= 16) {
			throw new IllegalArgumentException("Nonce size is incorrect. Make sure that the incoming data is an AES encrypted file.");
		}
		byte[] iv = new byte[noonceSize];
		byteBuffer.get(iv);

		// Prepare your password
		SecretKey secretKey = generateSecretKey(password, iv);

		// get the rest of encrypted data
		byte[] cipherBytes = new byte[byteBuffer.remaining()];
		byteBuffer.get(cipherBytes);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		// Encryption mode on!
		cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

		// Encrypt the data
		return cipher.doFinal(cipherBytes);
	}

	/**
	 * Function to generate a 128-bit key from the given password and iv
	 *
	 * @param password
	 * @param iv
	 * @return Secret key
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 */
	public static SecretKey generateSecretKey(char[] password, byte[] iv) throws NoSuchAlgorithmException, InvalidKeySpecException {
		KeySpec spec = new PBEKeySpec(password, iv, 65536, 128); // AES-128
		SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
		return new SecretKeySpec(key, "AES");
	}
}
