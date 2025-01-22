package com.satergo.extra;

import org.bouncycastle.crypto.PasswordConverter;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Uses Argon2
 */
public class NewAESEncryption implements Encryption {

	/** This is basically a smaller version of {@link Argon2Parameters} but without the salt. */
	public record Argon2Params(int type, int version, int memory, int iterations, int parallelism) {

		public Argon2Params {
			if (type < 0 || type > 2) throw new IllegalArgumentException();
			if (version != Argon2Parameters.ARGON2_VERSION_10 && version != Argon2Parameters.ARGON2_VERSION_13)
				throw new IllegalArgumentException();
			if (memory < 0) throw new IllegalArgumentException();
			if (iterations < 0 || iterations > 255) throw new IllegalArgumentException();
			if (parallelism < 0 || parallelism > 255) throw new IllegalArgumentException();
		}

		public Argon2Parameters toBCParameters(byte[] salt) {
			return new Argon2Parameters.Builder(type)
					.withVersion(version).withMemoryAsKB(memory).withIterations(iterations).withParallelism(parallelism)
					.withSalt(salt).build();
		}
	}

	private final Argon2Params argon2Params;

	public NewAESEncryption(Argon2Params argon2Params) {
		this.argon2Params = argon2Params;
	}

	@Override
	public byte[] encryptData(byte[] iv, SecretKey secretKey, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

		// Encrypt the data
		return cipher.doFinal(data);
	}

	@Override
	public byte[] decryptData(byte[] iv, SecretKey secretKey, byte[] encryptedData) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);

		cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

		// Decrypt the data
		return cipher.doFinal(encryptedData);
	}

	@Override
	public SecretKey generateSecretKey(char[] password, byte[] salt) {
		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(argon2Params.toBCParameters(salt));
		byte[] key = new byte[32]; // 32 for AES-256
		generator.generateBytes(PasswordConverter.UTF8.convert(password), key);
		return new SecretKeySpec(key, "AES");
	}
}
