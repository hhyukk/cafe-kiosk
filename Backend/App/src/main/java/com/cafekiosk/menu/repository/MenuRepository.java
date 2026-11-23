package com.cafekiosk.menu.repository;


import com.cafekiosk.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 판매중 메뉴만 걸러내는 일을 엔티티가 아니라 이 인터페이스가 한다.
 *
 * Hibernate 의 @SQLRestriction 을 Menu 에 붙이면 한 줄로 모든 조회가 걸러지지만
 * 그 필터는 OrderItem.menu 프록시를 초기화할 때도 함께 걸린다. 판매 중단된 메뉴를
 * 참조하는 과거 주문이 정작 그 메뉴를 읽지 못하게 되는데, 참조를 살려 두는 것이
 * 소프트 삭제의 목적이므로 목적을 정면으로 깨는 셈이다. 그래서 필터를 메서드로 명시한다.
 *
 * JpaRepository 가 주는 findById 와 findAll 은 필터 없이 남는다. 이것도 의도다.
 * 판매 중단 여부와 무관하게 행 자체를 봐야 하는 자리가 있다.
 *
 * deleteByIdAndEmail 은 지웠다. 하드 삭제 경로를 남겨 두면 언젠가 그리로 호출된다.
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    // 목록은 등록 순서다. 정렬 기준이 없으면 PostgreSQL 은 행이 저장된 순서대로 돌려주는데,
    // UPDATE 는 행의 새 버전을 테이블 뒤쪽에 쓰므로 점주가 고친 메뉴가 목록 끝으로 밀린다.
    // 손님 화면은 받은 순서를 그대로 그리므로 메뉴를 고칠 때마다 화면 배치가 바뀌었다.
    //
    // 정렬을 메서드 이름에 넣는다. @Query 에 숨기면 부르는 쪽이 이름만 보고 순서를 알 수 없고,
    // Sort 를 인자로 받으면 부르는 두 자리가 각자 정렬을 적다가 한쪽만 바뀐다.
    // id 는 IDENTITY 라 등록할수록 커지고 기본키 인덱스가 이미 있다. MenuControllerTest t06 이 지킨다.
    List<Menu> findAllByDeletedAtIsNullOrderByIdAsc();

    Optional<Menu> findByIdAndDeletedAtIsNull(Long id);
}
