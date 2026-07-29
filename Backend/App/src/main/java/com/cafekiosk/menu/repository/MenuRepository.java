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

    List<Menu> findAllByDeletedAtIsNull();

    Optional<Menu> findByIdAndDeletedAtIsNull(Long id);
}
