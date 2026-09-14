package com.wrb.devica.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.wrb.devica.product.CpuTier;
import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.recommendation.RecommendationResponse.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationResponseTest {

    private static final LaptopSpec MAC_SPEC = new LaptopSpec(Os.MAC, CpuTier.BASIC, 24, 512);
    private static final LaptopSpec WINDOWS_SPEC = new LaptopSpec(Os.WINDOWS, CpuTier.H, 16, 256);
    private static final Map<String, List<String>> ITEM_REASONS = Map.of(
        "OS", List.of("os 근거"),
        "CPU_TIER", List.of("cpu 근거", "cpu 보강 근거"),
        "MEMORY", List.of("memory 근거"),
        "STORAGE", List.of("storage 근거"));
    private static final List<RecommendedSpec> RECOMMENDED_SPECS = List.of(
        new RecommendedSpec(MAC_SPEC, ITEM_REASONS),
        new RecommendedSpec(WINDOWS_SPEC, ITEM_REASONS));

    @Test
    void 권장_사양_목록을_순서대로_모두_변환한다() {
        // when
        RecommendationResponse response = RecommendationResponse.from(RECOMMENDED_SPECS);

        // then
        assertThat(response.specs())
            .extracting(specResponse -> specResponse.items().getFirst().value())
            .containsExactly(Os.MAC.name(), Os.WINDOWS.name());
    }

    @Test
    void 사양_항목마다_표시_형태와_근거를_붙인다() {
        // when
        RecommendationResponse response = RecommendationResponse.from(RECOMMENDED_SPECS);

        // then
        assertThat(response.specs().getFirst().items())
            .extracting(ItemResponse::code, ItemResponse::displayName, ItemResponse::value,
                ItemResponse::displayValue, ItemResponse::reasons)
            .containsExactly(
                tuple("OS", "운영체제", "MAC", "Mac", List.of("os 근거")),
                tuple("CPU_TIER", "CPU", "BASIC", "M 칩", List.of("cpu 근거", "cpu 보강 근거")),
                tuple("MEMORY", "메모리", "24", "24GB", List.of("memory 근거")),
                tuple("STORAGE", "저장 공간", "512", "512GB", List.of("storage 근거")));
    }
}
