package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoupangProbeCredentialsTest {

    @TempDir
    Path directory;

    @Test
    void 키_두_개만_읽고_셸_명령이나_변수_치환을_실행하지_않는다() {
        // when
        var keys = CoupangProbeCredentials.parse(List.of("# comment", "MYSQL_PASSWORD=unrelated",
            "COUPANG_ACCESS_KEY='test-access'", "COUPANG_SECRET_KEY=\"$(command)=literal\""));

        // then
        assertThat(keys).containsExactlyInAnyOrderEntriesOf(Map.of(
            "COUPANG_ACCESS_KEY", "test-access", "COUPANG_SECRET_KEY", "$(command)=literal"));
    }

    @Test
    void 환경변수가_파일보다_우선한다() throws Exception {
        // given
        Path file = directory.resolve("keys.env");
        Files.writeString(file, "COUPANG_ACCESS_KEY=file-access\nCOUPANG_SECRET_KEY=file-secret\n");

        // when
        var signer = CoupangProbeCredentials.signer(Map.of("COUPANG_ACCESS_KEY", "env-access"), file,
            Clock.systemUTC());

        // then
        assertThat(signer.sign("GET", URI.create("https://api-gateway.coupang.com/test")))
            .contains("access-key=env-access").doesNotContain("file-access", "file-secret");
    }

    @Test
    void 중복된_키는_실수로_다른_계정을_선택하지_않도록_거절한다() {
        // when & then
        assertThatThrownBy(() -> CoupangProbeCredentials.parse(List.of(
            "COUPANG_ACCESS_KEY=first-secret", "COUPANG_ACCESS_KEY=second-secret")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("first-secret")
            .hasMessageNotContaining("second-secret");
    }
}
