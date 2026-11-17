package com.cafekiosk.auth.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 점주 계정. 바리스타도 같은 계정을 쓴다.
 *
 * ── 비밀번호 해시는 이 생성자가 만든다 ──────────────────────────────────────────────
 *
 * 생성자는 평문과 인코더를 받아 그 자리에서 해시를 만든다. 이미 만든 해시를 받는 생성자는 없다.
 * 해시를 받는 길을 열어 두면 언젠가 누군가 평문을 해시 자리에 넘기고, 그 행은 아무 예외 없이
 * 저장된다. 평문이 들어갈 경로 자체를 없앤다. 가격 스냅샷을 OrderItem 생성자가 메뉴에서 직접
 * 복사하는 것과 같은 이유다. 호출자에게 맡긴 규칙은 언젠가 빠진다.
 *
 * 엔티티가 PasswordEncoder 인터페이스에 기대는 것은 감수한다. 알고리즘을 고르는 일은 빈 설정이
 * 하고 엔티티는 넘겨받은 인코더를 쓰기만 하므로, BCrypt 를 다른 것으로 바꿔도 이 파일은 그대로다.
 *
 * ── 다른 테이블과 이어지지 않는다 ────────────────────────────────────────────────────
 *
 * menu.email 이 등록자를 문자열로 기록하지만 이 테이블을 외래키로 가리키지 않는다.
 * 그 컬럼은 기록일 뿐 권한 판단에 쓰지 않는다. 판단은 토큰이 들고 온 신원으로 한다.
 */
@Entity
@Table(name = "owner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    // BCrypt 해시는 60자지만 길이를 기본값 255 로 둔다. 알고리즘을 바꾸는 날 스키마를 건드리지 않는다
    @Column(nullable = false)
    private String password;

    // 순서값이 아니라 이름으로 저장한다. 상수가 하나 끼어드는 날 이미 저장된 행의 뜻이 밀리지 않게 한다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnerRole role;

    public Owner(String email, String rawPassword, PasswordEncoder passwordEncoder) {
        this.email = email;
        this.password = passwordEncoder.encode(rawPassword);
        this.role = OwnerRole.ROLE_OWNER;
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, this.password);
    }
}
