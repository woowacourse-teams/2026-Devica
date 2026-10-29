package com.wrb.devica.product.infra.coupang;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

/** 공통 Job을 한 번 실행한다. 프로세스 종료와 자원 정리는 애플리케이션 진입점에서 수행한다. */
final class CoupangPriceRefreshOnce implements ApplicationRunner, ExitCodeGenerator {

    private final CoupangPriceRefreshJob job;
    private int exitCode = 1;

    CoupangPriceRefreshOnce(CoupangPriceRefreshJob job) {
        this.job = job;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            exitCode = job.refreshPrices().exitCode();
        } catch (RuntimeException exception) {
            // Job이 실행 실패를 기록한다. 이미 완료한 상품은 유지하고 실패 종료 코드로 반환한다.
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
