package com.wrb.devica.product.infra.coupang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CoupangProbeCredentials {

    public static final String COUPANG_ACCESS_KEY = "COUPANG_ACCESS_KEY";
    public static final String COUPANG_SECRET_KEY = "COUPANG_SECRET_KEY";
    private static final Set<String> KEY_NAMES = Set.of(COUPANG_ACCESS_KEY, COUPANG_SECRET_KEY);

    private CoupangProbeCredentials() {
    }

    static CoupangHmacSigner signer(Map<String, String> environment, Path envFile, Clock clock) throws IOException {
        Map<String, String> keys = new HashMap<>();
        if (envFile != null) {
            keys.putAll(parse(Files.readAllLines(envFile)));
        }
        for (String name : KEY_NAMES) {
            String value = environment.get(name);
            if (value != null && !value.isBlank()) {
                keys.put(name, value);
            }
        }
        return new CoupangHmacSigner(keys.get(COUPANG_ACCESS_KEY), keys.get(COUPANG_SECRET_KEY), clock);
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> keys = new HashMap<>();
        for (String line : lines) {
            String[] pair = line.strip().split("=", 2);
            if (pair.length != 2 || !KEY_NAMES.contains(pair[0].strip())) {
                continue;
            }
            String value = pair[1].strip();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (keys.putIfAbsent(pair[0].strip(), value) != null) {
                throw new IllegalArgumentException("쿠팡 키가 환경변수 파일에 중복되어 있습니다.");
            }
        }
        return keys;
    }
}
