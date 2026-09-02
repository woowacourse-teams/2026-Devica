package com.wrb.devica.product;

import java.util.List;

/**
 * 제품마다 추천에 영향을 주는 요소를 담는다.
 * 값 전달은 values() 로 통일하고, 값 비교는 구현 타입으로 한다.
 */
public sealed interface Spec permits LaptopSpec {

    List<SpecValue> values();
}
