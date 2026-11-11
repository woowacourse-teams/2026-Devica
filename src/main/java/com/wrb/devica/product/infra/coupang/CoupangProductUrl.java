package com.wrb.devica.product.infra.coupang;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CoupangProductUrl(String pageId, String itemId, String vendorItemId) {

    private static final Set<String> PRODUCT_HOSTS = Set.of("www.coupang.com", "coupang.com", "m.coupang.com");
    private static final Set<String> IDENTIFIERS = Set.of("pageKey", "itemId", "vendorItemId");
    private static final Pattern PRODUCT_PATH = Pattern.compile("/(?:vp|vm)/products/([1-9][0-9]*)/?");

    public static Optional<CoupangProductUrl> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            return parseProductUrl(URI.create(url));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<CoupangProductUrl> parseProductUrl(URI uri) {
        if (!isSecureUrl(uri)) {
            return Optional.empty();
        }
        Map<String, String> query = identifiers(uri.getRawQuery());
        return pageId(uri, query)
            .map(pageId -> new CoupangProductUrl(pageId, query.get("itemId"), query.get("vendorItemId")));
    }

    private static boolean isSecureUrl(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static Optional<String> pageId(URI uri, Map<String, String> query) {
        if ("link.coupang.com".equals(uri.getHost()) && "/re/AFFSDP".equals(uri.getPath())) {
            return Optional.ofNullable(query.get("pageKey"));
        }
        if (uri.getHost() == null || !PRODUCT_HOSTS.contains(uri.getHost())) {
            return Optional.empty();
        }
        return pageIdFromPath(uri.getPath());
    }

    private static Optional<String> pageIdFromPath(String path) {
        Matcher productPath = PRODUCT_PATH.matcher(path);
        if (!productPath.matches()) {
            return Optional.empty();
        }
        return Optional.of(productPath.group(1));
    }

    public OptionMatch compareOption(CoupangProductUrl other) {
        if (itemId == null || vendorItemId == null || other.itemId == null || other.vendorItemId == null) {
            return OptionMatch.INSUFFICIENT_IDENTIFIERS;
        }
        if (itemId.equals(other.itemId) && vendorItemId.equals(other.vendorItemId)) {
            return OptionMatch.MATCHED_OPTION_IDS;
        }
        return OptionMatch.DIFFERENT_OPTION;
    }

    private static Map<String, String> identifiers(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null) {
            return values;
        }
        for (String parameter : rawQuery.split("&")) {
            addIdentifier(values, parameter);
        }
        return values;
    }

    private static void addIdentifier(Map<String, String> values, String parameter) {
        String[] pair = parameter.split("=", 2);
        String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
        if (!IDENTIFIERS.contains(key)) {
            return;
        }
        putIdentifier(values, key, identifierValue(pair));
    }

    private static String identifierValue(String[] pair) {
        if (pair.length < 2) {
            return "";
        }
        return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
    }

    private static void putIdentifier(Map<String, String> values, String key, String value) {
        if (!value.matches("[1-9][0-9]*") || values.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("상품 식별자가 누락되거나 중복되었습니다.");
        }
    }

    public enum OptionMatch {
        MATCHED_OPTION_IDS,
        DIFFERENT_OPTION,
        INSUFFICIENT_IDENTIFIERS,
        INVALID_PRODUCT_URL
    }
}
