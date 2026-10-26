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
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return Optional.empty();
            }
            Map<String, String> query = identifiers(uri.getRawQuery());
            String pageId;
            if ("link.coupang.com".equals(uri.getHost()) && "/re/AFFSDP".equals(uri.getPath())) {
                pageId = query.get("pageKey");
            } else if (uri.getHost() != null && PRODUCT_HOSTS.contains(uri.getHost())) {
                Matcher path = PRODUCT_PATH.matcher(uri.getPath());
                if (!path.matches()) {
                    return Optional.empty();
                }
                pageId = path.group(1);
            } else {
                return Optional.empty();
            }
            if (pageId == null) {
                return Optional.empty();
            }
            return Optional.of(new CoupangProductUrl(pageId, query.get("itemId"), query.get("vendorItemId")));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
            String[] pair = parameter.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (!IDENTIFIERS.contains(key)) {
                continue;
            }
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (!value.matches("[1-9][0-9]*") || values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("상품 식별자가 누락되거나 중복되었습니다.");
            }
        }
        return values;
    }

    public enum OptionMatch {
        MATCHED_OPTION_IDS,
        DIFFERENT_OPTION,
        INSUFFICIENT_IDENTIFIERS,
        INVALID_PRODUCT_URL
    }
}
