package com.cafekiosk.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점주 계정의 비밀번호 규칙 단위 테스트.
 * Spring 컨텍스트 없이 순수 POJO 로 본다. 인코더는 운영 빈과 같은 BCrypt 를 직접 만든다.
 */
class OwnerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("비밀번호는 원문이 아니라 BCrypt 해시로만 남는다")
    void should_비밀번호는_해시로만_남는다() {
        Owner owner = new Owner("owner@test.com", "plain-password", passwordEncoder);

        assertThat(owner.getPassword())
                .isNotEqualTo("plain-password")
                .doesNotContain("plain-password")
                // BCrypt 해시의 모양이다. docs/ERD.md 가 password 컬럼을 이 길이로 설명한다
                .startsWith("$2")
                .hasSize(60);
    }

    @Test
    @DisplayName("맞는 비밀번호만 통과한다")
    void should_맞는_비밀번호만_통과한다() {
        Owner owner = new Owner("owner@test.com", "plain-password", passwordEncoder);

        assertThat(owner.matchesPassword("plain-password", passwordEncoder)).isTrue();
        assertThat(owner.matchesPassword("wrong-password", passwordEncoder)).isFalse();
    }

    @Test
    @DisplayName("새 계정의 역할은 ROLE_OWNER 다")
    void should_새_계정은_점주_역할이다() {
        Owner owner = new Owner("owner@test.com", "plain-password", passwordEncoder);

        assertThat(owner.getRole()).isEqualTo(OwnerRole.ROLE_OWNER);
    }
}
