package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoupangProductUrlTest {

    private final CoupangProductUrl expected = new CoupangProductUrl("9410641464", "27961674147", "94919713475");

    @Test
    void 파트너스_링크의_페이지와_옵션_ID를_분리한다() {
        var parsed = CoupangProductUrl.parse("https://link.coupang.com/re/AFFSDP?lptag=AF123"
            + "&pageKey=9410641464&itemId=27961674147&vendorItemId=94919713475&traceid=ignored");

        assertThat(parsed).contains(expected);
    }

    @Test
    void 같은_페이지의_다른_RAM_SSD_옵션을_같은_판매상품으로_판단하지_않는다() {
        var other = CoupangProductUrl.parse("https://www.coupang.com/vp/products/9410641464"
            + "?itemId=27961674159&vendorItemId=94919713484").orElseThrow();

        assertThat(expected.compareOption(other)).isEqualTo(CoupangProductUrl.OptionMatch.DIFFERENT_OPTION);
    }

    @Test
    void 페이지가_변해도_옵션_ID_일치_여부는_별도로_판단한다() {
        var other = new CoupangProductUrl("123", "27961674147", "94919713475");

        assertThat(expected.compareOption(other)).isEqualTo(CoupangProductUrl.OptionMatch.MATCHED_OPTION_IDS);
    }

    @Test
    void vendorItemId가_같아도_itemId가_충돌하면_자동으로_일치시키지_않는다() {
        assertThat(expected.compareOption(new CoupangProductUrl("9410641464", "999", "94919713475")))
            .isEqualTo(CoupangProductUrl.OptionMatch.DIFFERENT_OPTION);
    }

    @Test
    void 옵션_ID가_누락되면_페이지가_같아도_확정하지_않는다() {
        var other = CoupangProductUrl.parse("https://www.coupang.com/vp/products/9410641464").orElseThrow();

        assertThat(expected.compareOption(other)).isEqualTo(CoupangProductUrl.OptionMatch.INSUFFICIENT_IDENTIFIERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com/vp/products/123?itemId=1&vendorItemId=2",
        "https://www.coupang.com.example.com/vp/products/123?itemId=1&vendorItemId=2",
        "https://name@www.coupang.com/vp/products/123?itemId=1&vendorItemId=2",
        "https://link.coupang.com/re/AFFSRP?pageKey=laptop",
        "https://www.coupang.com/vp/products/123?itemId=1&vendorItemId=2&vendorItemId=3",
        "https://www.coupang.com/vp/products/123?itemId=1&vendorItemId=",
        "https://www.coupang.com/vp/products/123?itemId=1&vendorItemId=abc",
        "https://www.coupang.com/vp/products/123?itemId=0&vendorItemId=2",
        "https://www.coupang.com/vp/products/123?itemId=%ZZ&vendorItemId=2",
        "http://www.coupang.com/vp/products/123?itemId=1&vendorItemId=2",
        "not a url"
    })
    void 잘못되거나_모호한_상품_URL을_거절한다(String url) {
        assertThat(CoupangProductUrl.parse(url)).isEmpty();
    }
}
