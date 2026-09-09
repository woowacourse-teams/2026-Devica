package com.wrb.devica.purpose;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

// 조회는 enum 으로 하므로 시드가 어긋나도 API 에는 증상이 없다
@SpringBootTest
@Sql("/data.sql")
@Transactional
class UsagePurposeSeedTest {

    @Autowired
    private UsagePurposeRepository usagePurposeRepository;

    @Test
    void 시드된_사용_목적과_enum이_일대일로_대응한다() {
        // when
        List<UsagePurposeCode> seeded = usagePurposeRepository.findAll().stream()
                .map(UsagePurpose::getCode)
                .toList();

        // then
        assertThat(seeded).containsExactlyInAnyOrder(UsagePurposeCode.values());
    }
}
