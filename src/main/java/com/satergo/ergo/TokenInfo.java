package com.satergo.ergo;

public record TokenInfo(String id, String boxId, long emissionAmount, String name, String description, String type, int decimals) implements TokenSummary {
}
