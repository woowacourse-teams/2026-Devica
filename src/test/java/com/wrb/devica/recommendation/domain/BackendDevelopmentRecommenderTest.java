package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.LaptopSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackendDevelopmentRecommenderTest {

    @Test
    void CPU_점수가_2에서_3이_되면_한_단계_오른다() {
        // given
        BackendDevelopmentRecommender recommender = new BackendDevelopmentRecommender();
        Requirements two = new Requirements(Map.of("REQUIRED_CPU", 2), Map.of());
        Requirements three = new Requirements(Map.of("REQUIRED_CPU", 3), Map.of());

        // when
        List<RecommendedSpec> atTwo = recommender.recommend(two, Map.of());
        List<RecommendedSpec> atThree = recommender.recommend(three, Map.of());

        // then
        assertThat(((LaptopSpec) atTwo.get(0).spec()).cpuTier()).isEqualTo(CpuTier.BASIC);
        assertThat(((LaptopSpec) atTwo.get(1).spec()).cpuTier()).isEqualTo(CpuTier.P_HS);
        assertThat(((LaptopSpec) atThree.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) atThree.get(1).spec()).cpuTier()).isEqualTo(CpuTier.H);
    }

    @Test
    void CPU_점수가_4에서_5가_되면_Windows_는_한_단계_오르고_Mac_은_M_Pro_칩으로_제한한다() {
        // given
        BackendDevelopmentRecommender recommender = new BackendDevelopmentRecommender();
        Requirements four = new Requirements(Map.of("REQUIRED_CPU", 4), Map.of());
        Requirements five = new Requirements(Map.of("REQUIRED_CPU", 5), Map.of());

        // when
        List<RecommendedSpec> atFour = recommender.recommend(four, Map.of());
        List<RecommendedSpec> atFive = recommender.recommend(five, Map.of());

        // then
        assertThat(((LaptopSpec) atFour.get(1).spec()).cpuTier()).isEqualTo(CpuTier.H);
        assertThat(((LaptopSpec) atFive.get(1).spec()).cpuTier()).isEqualTo(CpuTier.HX);
        assertThat(((LaptopSpec) atFour.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) atFive.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(atFive.get(0).itemReasons().get("REQUIRED_CPU"))
            .contains("예산을 고려해 Mac 권장 CPU 는 M Pro 칩으로 제한했습니다.");
    }

    @Test
    void Mac_은_메모리가_CPU_등급의_상한을_넘으면_CPU_를_올린다() {
        // given
        BackendDevelopmentRecommender recommender = new BackendDevelopmentRecommender();
        Requirements requirements = new Requirements(Map.of("MEMORY", 7), Map.of());

        // when
        LaptopSpec mac = (LaptopSpec) recommender.recommend(requirements, Map.of()).get(0).spec();

        // then
        assertThat(mac.cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(mac.memoryGb()).isEqualTo(48);
    }

    @Test
    void Mac_은_CPU_등급에_없는_메모리를_가장_가까운_큰_용량으로_맞춘다() {
        // given
        BackendDevelopmentRecommender recommender = new BackendDevelopmentRecommender();
        Requirements requirements = new Requirements(Map.of("REQUIRED_CPU", 3, "MEMORY", 4), Map.of());

        // when
        RecommendedSpec mac = recommender.recommend(requirements, Map.of()).get(0);
        RecommendedSpec windows = recommender.recommend(requirements, Map.of()).get(1);

        // then
        assertThat(((LaptopSpec) mac.spec()).memoryGb()).isEqualTo(48);
        assertThat(mac.itemReasons().get("MEMORY")).contains("M Pro 칩 에서 고를 수 있는 가장 가까운 용량은 48GB 입니다.");
        assertThat(((LaptopSpec) windows.spec()).memoryGb()).isEqualTo(32);
    }

    @Test
    void 지금_쓰는_CPU_가_같은_OS_면_그보다_한_단계_위를_권한다() {
        // given
        BackendDevelopmentRecommender recommender = new BackendDevelopmentRecommender();
        Requirements requirements = new Requirements(Map.of(), Map.of());
        Map<String, String> conditions = Map.of("CURRENT_CPU_TIER", "BASIC");

        // when
        List<RecommendedSpec> specs = recommender.recommend(requirements, conditions);

        // then
        assertThat(((LaptopSpec) specs.get(0).spec()).cpuTier()).isEqualTo(CpuTier.PRO);
        assertThat(((LaptopSpec) specs.get(1).spec()).cpuTier()).isEqualTo(CpuTier.P_HS);
    }

    @Test
    void 요구_항목의_근거_뒤에_결과_문장을_붙인다() {
        // given
        BackendDevelopmentRecommender recommender = new BackendDevelopmentRecommender();
        Requirements requirements = new Requirements(Map.of("MEMORY", 2), Map.of("MEMORY", List.of("자주 느려집니다.")));

        // when
        RecommendedSpec mac = recommender.recommend(requirements, Map.of()).get(0);

        // then
        assertThat(mac.itemReasons().get("MEMORY")).containsExactly("자주 느려집니다.", "권장 메모리는 24GB 입니다.");
    }
}
