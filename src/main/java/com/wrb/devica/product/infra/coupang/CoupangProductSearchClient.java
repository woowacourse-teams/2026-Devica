package com.wrb.devica.product.infra.coupang;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class CoupangProductSearchClient {

    private static final String SEARCH_URL = "https://api-gateway.coupang.com"
        + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";

    private final RestClient restClient;
    private final CoupangHmacSigner signer;

    public CoupangProductSearchClient(RestClient restClient, CoupangHmacSigner signer) {
        this.restClient = restClient;
        this.signer = signer;
    }

    public List<Product> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해야 합니다.");
        }
        URI uri = URI.create(SEARCH_URL + "?keyword="
            + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=10");
        try {
            SearchResponse response = restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, signer.sign("GET", uri))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), (request, httpResponse) -> {
                    throw new CoupangApiException(httpFailure(httpResponse.getStatusCode().value()));
                })
                .body(SearchResponse.class);
            if (response == null || response.rCode() == null) {
                throw new CoupangApiException(CoupangApiException.Reason.INVALID_RESPONSE);
            }
            if (!"0".equals(response.rCode())) {
                throw new CoupangApiException(CoupangApiException.Reason.API_REJECTED, response.rCode());
            }
            if (response.data() == null || response.data().productData() == null
                || response.data().productData().stream().anyMatch(Objects::isNull)) {
                throw new CoupangApiException(CoupangApiException.Reason.INVALID_RESPONSE);
            }
            return List.copyOf(response.data().productData());
        } catch (RestClientResponseException exception) {
            throw new CoupangApiException(httpFailure(exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            throw new CoupangApiException(CoupangApiException.Reason.CONNECTION_FAILED);
        } catch (RestClientException exception) {
            throw new CoupangApiException(CoupangApiException.Reason.INVALID_RESPONSE);
        }
    }

    private CoupangApiException.Reason httpFailure(int status) {
        if (status == 401 || status == 403) {
            return CoupangApiException.Reason.AUTHENTICATION_FAILED;
        }
        if (status == 429) {
            return CoupangApiException.Reason.RATE_LIMITED;
        }
        return CoupangApiException.Reason.HTTP_ERROR;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(String productId, String productName, Long productPrice, String productUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(String rCode, SearchData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchData(List<Product> productData) {
    }
}
