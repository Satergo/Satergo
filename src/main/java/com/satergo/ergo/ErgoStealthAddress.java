package com.satergo.ergo;

import org.bouncycastle.math.ec.ECPoint;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.NetworkType;
import scorex.crypto.hash.Blake2b256;
import scorex.util.encode.Base58;
import sigmastate.Values;
import sigmastate.crypto.CryptoConstants;
import sigmastate.crypto.Platform;
import sigmastate.serialization.ErgoTreeSerializer;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

public class ErgoStealthAddress {

	private final String content;
	private final byte[] mainAddress;

	public ErgoStealthAddress(String content) {
		if (!content.startsWith("stealth")) {
			throw new IllegalArgumentException("Not a stealth address");
		}
		byte[] decodedAddress = Base58.decode(content.substring(7)).get();
		this.mainAddress = Arrays.copyOfRange(decodedAddress, 0, decodedAddress.length - 4);
		byte[] addressChecksum = Arrays.copyOfRange(decodedAddress, decodedAddress.length - 4, decodedAddress.length);
		byte[] first4HashBytes = Arrays.copyOf((byte[]) Blake2b256.hash(mainAddress), 4);
		if (!Arrays.equals(addressChecksum, first4HashBytes)) {
			throw new IllegalArgumentException("Invalid checksum");
		}
		this.content = content;
	}

	private static Values.ErgoTree generateStealthPaymentErgoTree(byte[] gr, byte[] gy, byte[] ur, byte[] uy) {
		HexFormat hf = HexFormat.of();
		return ErgoTreeSerializer.DefaultSerializer().deserializeErgoTree(hf.parseHex(
				"10040e21" + hf.formatHex(gr) + "0e21" + hf.formatHex(gy)
						+ "0e21" + hf.formatHex(ur) + "0e21" + hf.formatHex(uy) + "ceee7300ee7301ee7302ee7303"
		));
	}

	public Address generatePaymentAddress(NetworkType networkType) {
		ECPoint u = Platform.createContext().decodePoint(mainAddress).value();
		ECPoint g = CryptoConstants.dlogGroup().generator().value();
		SecureRandom secureRandom = new SecureRandom();
		byte[] rBytes = new byte[32], yBytes = new byte[32];
		secureRandom.nextBytes(rBytes);
		secureRandom.nextBytes(yBytes);
		BigInteger r = new BigInteger(rBytes);
		BigInteger y = new BigInteger(yBytes);

		Values.ErgoTree tree = generateStealthPaymentErgoTree(
				g.multiply(r).getEncoded(true), g.multiply(y).getEncoded(true),
				u.multiply(r).getEncoded(true), u.multiply(y).getEncoded(true));
		return Address.fromErgoTree(tree, networkType);
	}

	@Override
	public String toString() {
		return content;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ErgoStealthAddress a && content.equals(a.content);
	}

	@Override
	public int hashCode() {
		return content.hashCode();
	}
}
