package com.cafekiosk.auth.entity;

/**
 * 점주 계정의 역할. 지금은 하나뿐이다.
 *
 * 바리스타와 점주를 하나로 묶은 것은 1인 매장을 기준으로 삼은 의도한 단순화다. 역할이 하나라
 * 권한 테이블을 따로 두지 않고 owner.role 컬럼 하나로 끝낸다. 둘째 역할이 생기는 날 쪼갠다.
 *
 * 상수 이름이 ROLE_ 로 시작하는 이유는 이 글자가 그대로 DB 에 저장되고, 그대로 토큰의 role
 * 클레임에 실리고, 그대로 Spring Security 의 권한 이름이 되기 때문이다. 세 자리가 같은 글자를
 * 쓰면 중간에 접두를 붙였다 떼는 변환이 없다. hasRole("OWNER") 가 ROLE_OWNER 를 찾는 것은
 * Spring Security 의 규칙이다.
 */
public enum OwnerRole {
    ROLE_OWNER
}
