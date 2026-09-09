package com.wrb.devica.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * DevicaApplication 에 직접 붙이면 @WebMvcTest 가 그 클래스를 설정 원본으로 읽으면서
 * JPA 없이 auditing 빈을 만들려다 실패한다. 별도 설정으로 두면 웹 계층 테스트가 이 클래스를 건너뛴다.
 * 저장소 테스트에서 생성·수정 시각이 필요하면 @Import(JpaAuditingConfig.class) 를 붙인다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
