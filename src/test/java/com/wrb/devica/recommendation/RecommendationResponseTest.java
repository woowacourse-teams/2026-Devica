package com.wrb.devica.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.wrb.devica.product.LaptopSpec;
import com.wrb.devica.product.Os;
import com.wrb.devica.product.SpecItem;
import com.wrb.devica.recommendation.RecommendationResponse.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationResponseTest {

    private static final LaptopSpec MAC_SPEC = new LaptopSpec(Os.MACOS, "M 칩", 24, 512);
    private static final LaptopSpec WINDOWS_SPEC = new LaptopSpec(Os.WINDOWS, "Core Ultra 7", 16, 256);
    private static final Map<String, String> ITEM_REASONS = Map.of(
        "OS", "os 근거",
        "CPU", "cpu 근거",
        "MEMORY", "memory 근거",
        "STORAGE", "storage 근거");
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
            .containsExactly(Os.MACOS.name(), Os.WINDOWS.name());
    }

    @Test
    void 사양_항목의_각_필드를_응답_항목의_같은_필드로_옮긴다() {
        // given
        SpecItem specItem = MAC_SPEC.toItems().getFirst();

        // when
        RecommendationResponse response = RecommendationResponse.from(RECOMMENDED_SPECS);

        // then
        ItemResponse itemResponse = response.specs().getFirst().items().getFirst();

        assertThat(itemResponse.code()).isEqualTo(specItem.code());
        assertThat(itemResponse.displayName()).isEqualTo(specItem.displayName());
        assertThat(itemResponse.value()).isEqualTo(specItem.value());
        assertThat(itemResponse.displayValue()).isEqualTo(specItem.displayValue());
    }

    @Test
    void 권장_사양의_각_항목마다_선택_근거를_붙인다() {
        // when
        RecommendationResponse response = RecommendationResponse.from(RECOMMENDED_SPECS);

        // then
        assertThat(response.specs().getFirst().items())
            .extracting(ItemResponse::code, ItemResponse::reason)
            .containsExactly(
                tuple("OS", "os 근거"),
                tuple("CPU", "cpu 근거"),
                tuple("MEMORY", "memory 근거"),
                tuple("STORAGE", "storage 근거"));
    }
}
