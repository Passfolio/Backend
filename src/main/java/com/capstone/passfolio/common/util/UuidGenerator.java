package com.capstone.passfolio.common.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UuidGenerator {
    public static UUID generate(String data) {
        return UUID.nameUUIDFromBytes(
                data.getBytes(StandardCharsets.UTF_8)
        );
    }
}
