package com.satergo.ergo;

import org.ergoplatform.appkit.Address;

public record MiningPoolInfo(String jarFileName, int port, Address address) {
}
