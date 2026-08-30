# cafe-kiosk API 명세서

최초 작성 2026-07-23

이 문서는 완성된 cafe-kiosk의 HTTP 계약을 담는다. 각 엔드포인트의 경로, 메서드, 인증, 요청, 응답, 상태 코드를 적고 `docs/REQUIREMENTS.md`의 FR 번호와 연결한다. 완성 기준 목표 API로 서술하되, 지금 코드가 목표와 다른 지점은 각 자리에 현재 구현 메모로 붙여 정직하게 구분한다. 요구사항의 근거와 왜 그렇게 정했는지는 REQUIREMENTS.md가 소유하고, 이 문서는 그 요구사항이 HTTP 위에서 어떤 모양이 되는지를 소유한다.

## 1. 개요

대상 독자는 프론트엔드와 백엔드를 잇는 개발자다. 이 문서는 두 계층을 다룬다. 하나는 백엔드 REST API로, com.cafekiosk의 컨트롤러가 노출하는 서버 계약이며 이 문서의 정본이다. 다른 하나는 BFF 프록시로, 브라우저가 실제로 부르는 Next.js 라우트 핸들러다.

브라우저는 백엔드를 직접 부르지 않는다. 제약 C-04에 따라 모든 요청은 BFF를 경유한다. 따라서 브라우저 관점의 베이스 URL은 프론트엔드 자신이고, BFF가 백엔드로 중계한다.

| 계층 | 베이스 URL | 부르는 주체 |
| --- | --- | --- |
| BFF 프록시 | `http://localhost:3000/api` | 브라우저 |
| 백엔드 REST | `http://localhost:8080/api` | BFF |

현재 구현 메모. BFF 라우트가 백엔드 주소 `http://localhost:8080`을 코드에 하드코딩한다. 배포 단계에서 환경 변수로 걷어낸다. NFR-OPS-02.

## 2. 공통 규약

### 2-1. 응답 봉투

목표 표준 봉투는 `RsData`다. 근거는 `global/rsData/RsData.java`다.

```json
{
  "resultCode": "200-1",
  "message": "사람이 읽을 메시지",
  "data": { }
}
```

resultCode는 상태 코드에 일련번호를 붙인 문자열이다. 200-1, 400-1, 404-1, 409-1처럼 쓴다. data는 성공 응답의 본문이며 오류 응답에서는 비운다.

현재 구현 메모. 봉투가 아직 일관되지 않다. 메뉴 목록은 맨 배열로, 메뉴 생성과 삭제는 평문 문자열로, 주문 생성은 봉투 없는 DTO로, 업로드는 Map으로 내려간다. RsData로 감싸는 것은 오류 응답과 일부 성공 응답뿐이다. 목표는 모든 응답을 RsData로 통일하는 것이다.

### 2-2. 오류 코드

오류는 `global/globalExceptionHandler/GlobalExceptionHandler.java`가 RsData로 정규화한다.

| HTTP | resultCode | 발생 상황 | 예외 |
| --- | --- | --- | --- |
| 400 | 400-1 | 본문 검증 실패, 잘못된 파라미터, 읽을 수 없는 본문 | MethodArgumentNotValidException, IllegalArgumentException, MethodArgumentTypeMismatchException, HttpMessageNotReadableException |
| 401 | 미정 | 인증 실패, 토큰 없음 | 목표, Spring Security |
| 404 | 404-1 | 대상 데이터 없음 | NoSuchElementException |
| 409 | 409-1 | 허용되지 않은 주문 상태 전이, 재고 부족, 낙관적 락 재시도 소진 | InvalidOrderStatusTransitionException, OutOfStockException, OptimisticLockingFailureException |

### 2-3. 인증

목표 인증은 stateless JWT다. 서명은 HS256, 키는 32바이트 이상이다. FR-AUTH-03, NFR-SEC-05. 점주가 로그인하면 Access 토큰을 받고, 토큰은 BFF가 httpOnly 쿠키로 보관한다. 브라우저 localStorage에 두지 않는다. FR-AUTH-08. 점주 전용 API를 토큰 없이 부르면 401이다. FR-AUTH-04. 손님 엔드포인트는 인증을 요구하지 않는다. FR-AUTH-06. 역할은 ROLE_OWNER 하나다. FR-AUTH-07.

인가는 서버가 검증한 신원에 근거한다. 요청 본문의 이메일을 비교해 권한을 판단하지 않는다. FR-AUTH-05, NFR-SEC-06.

현재 구현 메모. 인증이 아직 없다. 메뉴 수정과 삭제는 본문 이메일을 비교하는 수준이고, 주방과 관리자 API는 아무 보호가 없다. 아래 상세에서 인증이 필요 이라 적은 엔드포인트는 지금은 열려 있다.

### 2-4. CORS

`global/config/WebConfig.java`가 CORS를 연다. 허용 출처는 `http://localhost:3000`, 자격 증명 허용, maxAge 3600이다.

현재 구현 메모. 허용 메서드가 GET, POST, PUT, DELETE, OPTIONS다. PATCH가 빠져 있어 브라우저에서 주문 상태 변경 PATCH를 직접 부르면 CORS로 막힌다. 목표는 PATCH를 허용 메서드에 넣는 것이다.

## 3. 엔드포인트 요약

| 메서드 | 경로 | 인증 | 하는 일 | FR |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/login` | 없음 | 점주 로그인, 토큰 발급 | FR-AUTH-01 |
| POST | `/api/auth/logout` | ROLE_OWNER | 로그아웃, 쿠키 삭제 | FR-AUTH-09 |
| GET | `/api/menu` | 없음 | 메뉴 목록 조회, 재고 포함 | FR-MNU-01, FR-KSK-08 |
| POST | `/api/menu` | ROLE_OWNER | 메뉴 등록 | FR-MNU-02 |
| PUT | `/api/menu/modify/{id}` | ROLE_OWNER | 메뉴 수정 | FR-MNU-02 |
| DELETE | `/api/menu/delete/{menu_id}` | ROLE_OWNER | 메뉴 삭제 | FR-MNU-02 |
| POST | `/api/order` | 없음 | 주문 생성, 재고 차감 | FR-KSK-03, FR-STK-02 |
| GET | `/api/order/{orderNumber}` | 없음 | 대기번호로 자기 주문 조회 | FR-KSK-06 |
| GET | `/api/orders` | ROLE_OWNER | 주방과 점주용 주문 목록 | FR-KIT-01 |
| PATCH | `/api/order/{orderId}/status` | ROLE_OWNER | 주문 상태 전이 | FR-ORD-01, FR-KIT-04 |
| PATCH | `/api/stock/{menuId}` | ROLE_OWNER | 재고 조정 | FR-ADM-02 |
| POST | `/api/upload/image` | ROLE_OWNER | 메뉴 이미지 업로드 | FR-FILE-01 |

## 4. 도메인별 상세

### 4-1. 인증 auth

두 엔드포인트 전부 목표다. 지금은 없다.

#### POST /api/auth/login

점주가 이메일과 비밀번호로 로그인한다. FR-AUTH-01.

요청

```json
{
  "email": "owner@cafe.com",
  "password": "비밀번호"
}
```

응답 200. 토큰은 BFF가 응답에서 꺼내 httpOnly 쿠키로 심는다. 브라우저로 토큰 본문을 넘기지 않는다.

```json
{
  "resultCode": "200-1",
  "message": "로그인되었습니다.",
  "data": { "email": "owner@cafe.com", "role": "ROLE_OWNER" }
}
```

상태 코드. 200 성공, 401 인증 실패. 실패 메시지는 이메일과 비밀번호 중 어느 쪽이 틀렸는지 알리지 않는다. 계정 존재 여부가 새지 않게 한다. 화면 SC-03.

#### POST /api/auth/logout

로그아웃은 BFF가 쿠키를 지우는 것으로 성립한다. FR-AUTH-09. 응답 200.

### 4-2. 메뉴 menu

#### GET /api/menu

판매중인 메뉴 목록을 조회한다. 익명을 포함해 누구나 부른다. FR-MNU-01. 근거는 `MenuController.getMenus`, `MenuService.findAllWithStock`, `MenuDto.MenuListResponse`.

판매를 중단한 메뉴는 이 목록에서 빠진다. FR-MNU-07. 행은 남아 있지만 `deleted_at`이 채워진 메뉴는 `MenuRepository.findAllByDeletedAtIsNull`이 걸러낸다.

응답 200. 남은 재고와 품절 여부를 함께 내려준다. 손님 화면이 품절을 표시하고 담기를 막는 데 쓴다. FR-KSK-08. 재고 행이 없는 메뉴는 stock이 null이고 sold_out이 true다. 없는 것과 0개인 것은 다른 사실이므로 0으로 뭉개지 않는다.

```json
[
  {
    "menu_id": 1,
    "category": "커피",
    "menu_name": "에티오피아 예가체프",
    "price": 4500,
    "img_url": "/uploads/ethiopia.jpg",
    "stock": 100,
    "sold_out": false
  }
]
```

현재 구현 메모. 필드가 snake_case다. menu_id, category, menu_name, price, img_url, stock, sold_out. 응답이 RsData로 감싸이지 않고 맨 배열로 내려간다. 봉투를 통일하는 것은 Phase 3이다.

재고를 붙이는 방식은 조인이 아니다. 메뉴를 한 번 재고를 한 번 읽고 메모리에서 menuId로 맞춘다. 메뉴가 몇 개든 쿼리는 두 방이다. `docs/ERD.md` 4절이 menu와 stock을 단방향으로 두고 양방향 연관을 금지했는데, 조립 방식은 SQL 조인조차 만들지 않으므로 메뉴를 읽는 다른 경로에 재고가 따라 올라올 여지가 없다. 품절 판정은 `Stock.isSoldOut`이 소유하고 조회 경로가 수량을 꺼내 계산하지 않는다.

#### POST /api/menu

메뉴를 등록한다. 목표는 점주 인증이다. FR-MNU-02. 가격은 0원 이상 10,000,000원 이하다. FR-MNU-03. 근거는 `MenuController.createMenu`, `CreateMenuRequestDto`.

요청

```json
{
  "category": "커피",
  "menuName": "콜롬비아 수프리모",
  "price": 5000,
  "imageURL": "/uploads/colombia.jpg"
}
```

응답 201.

```json
{ "resultCode": "201-1", "message": "생성 완료되었습니다.", "data": { "menu_id": 4 } }
```

상태 코드. 201 생성, 400 가격 범위 위반이나 검증 실패, 401 인증 없음.

메뉴 등록은 재고 행을 같은 트랜잭션에서 함께 만든다. `docs/ERD.md` 3-4가 재고 없는 메뉴를 다루는 방법이 아니라 만들지 않는 방법을 규정했기 때문이다. 초기 수량은 0이라 새로 등록한 메뉴는 품절 상태로 태어나고, 팔 수 있게 만드는 일은 `PATCH /api/stock/{menuId}`가 따로 한다. 그래서 요청 본문에 수량 필드가 없다. 판단은 `docs/ADR/ADR-0004`에 있다.

현재 구현 메모. 요청 본문에 email 필드가 있고 등록자로 저장된다. 목표에서는 서버가 검증한 신원으로 대체하므로 본문에서 email이 빠진다. FR-MNU-06, SC-05. 응답이 지금은 평문 문자열 생성 완료되었습니다이며 RsData가 아니다.

#### PUT /api/menu/modify/{id}

메뉴의 이름, 가격, 이미지, 분류를 수정한다. 목표는 점주 인증이다. 근거는 `MenuController.modifyMenu`, `MenuDto.MenuModifyRequest`.

요청

```json
{
  "menu_name": "에티오피아 예가체프",
  "price": 4800,
  "img_url": "/uploads/ethiopia.jpg",
  "category": "커피"
}
```

응답 200.

```json
{
  "resultCode": "200-1",
  "message": "메뉴를 수정하였습니다.",
  "data": { "menu_id": 1, "menu_name": "에티오피아 예가체프", "price": 4800, "category": "커피" }
}
```

상태 코드. 200 수정 완료, 400 검증 실패, 404 없는 메뉴, 401 권한 없음.

현재 구현 메모. 요청 본문에 email이 있고, 그 값이 메뉴 등록자와 일치할 때만 수정된다. 불일치면 빈 본문 401이다. 목표에서는 JWT 인가로 대체되어 email이 빠진다. FR-AUTH-05. 없는 메뉴 id는 `MenuController.modifyMenu`가 Optional.get을 직접 불러 NoSuchElementException이 나고 404로 처리된다.

#### DELETE /api/menu/delete/{menu_id}

메뉴를 삭제한다. 목표는 점주 인증이다. 근거는 `MenuController.deleteMenu`, `DeleteMenuRequestDto`, `MenuService.deleteMenu`.

**행을 지우지 않고 판매를 중단한다.** FR-MNU-07. `menu.deleted_at`에 시각을 찍는 UPDATE 한 번이 나가고, 그 뒤로 이 메뉴는 목록 조회와 수정과 주문에서 전부 빠진다. 과거 주문은 이 메뉴를 그대로 참조한다.

하드 삭제를 버린 이유는 `order_item.menu_id`와 `stock.menu_id`가 이 행을 참조하기 때문이다. 참조가 있는 메뉴는 외래키 위반으로 지워지지 않고, 참조를 끊고 지우면 과거 주문이 무엇을 팔았는지 알 수 없게 된다. 자세한 판단은 `docs/ERD.md` 3-3에 있다.

응답 200.

```json
{ "resultCode": "200-1", "message": "삭제되었습니다.", "data": null }
```

상태 코드. 200 삭제, 401 권한 없음.

이미 판매를 중단한 메뉴를 다시 부르면 401이다. 조회에서 걸러지므로 없는 메뉴와 구분되지 않는다. 응답 코드를 둘로 가르지 않은 이유는 이 엔드포인트가 지금 200과 401 두 갈래만 갖고 있고, 그 계약을 손대는 일은 Phase 3의 인가 작업에 속하기 때문이다.

현재 구현 메모. 요청 본문에 email이 있고, 등록자와 일치할 때만 판매가 중단된다. 불일치면 401 평문 문자열 이메일이 잘못되었거나 삭제 권한이 없습니다다. 목표에서는 JWT 인가로 대체되어 email이 빠진다. 성공 응답은 평문 문자열 삭제되었습니다이며 RsData가 아니다. 소프트 삭제로 바뀐 뒤에도 이 문자열을 바꾸지 않았다. 겉으로 드러나는 계약은 그대로 두고 안쪽 메커니즘만 바꾸는 편이 BFF와 프론트를 건드리지 않아서다.

### 4-3. 주문 order

#### POST /api/order

손님이 주문을 생성한다. 인증하지 않는다. FR-KSK-01. 이메일 하나와 메뉴 목록을 받아 대기번호와 결제 금액을 돌려준다. FR-KSK-02, FR-KSK-03. 근거는 `OrderController.createOrder`, `OrderDto.CreateRequest`.

한 주문의 총 수량은 1개 이상 100개 이하다. 벗어나면 400이다. FR-KSK-07.

요청

```json
{
  "email": "guest@example.com",
  "items": [
    { "menuId": 1, "count": 2 },
    { "menuId": 3, "count": 1 }
  ]
}
```

응답 200.

```json
{
  "resultCode": "200-1",
  "message": "주문이 완료되었습니다.",
  "data": { "orderNumber": "0007", "totalPrice": 14000 }
}
```

상태 코드. 200 성공, 400 수량 위반이나 검증 실패나 없는 메뉴, 409 재고 부족과 낙관적 락 재시도 소진, 500 재고 행이 없는 메뉴.

주문 생성이 아이템 수량만큼 재고를 차감한다. FR-STK-02. 재고가 부족하면 주문 전체를 실패시키고 409를 준다. 부분 차감은 없다. FR-STK-03. 주문 생성과 재고 차감은 한 트랜잭션에서 전부 성공하거나 전부 실패한다. FR-ORD-08. 대기번호는 주문 PK에서 파생하며 전역 유일하고 단조 증가한다. FR-ORD-07. 각 아이템은 주문 시점의 가격과 이름을 스냅샷으로 굳힌다. FR-ORD-05, FR-ORD-11.

재고에 닿는 순서는 menuId 오름차순으로 고정한다. 요청 본문의 아이템 순서와 무관하다. 락이 붙은 지금은 이 정렬이 실제로 데드락을 막는다. 두 손님이 같은 두 메뉴를 반대 순서로 담으면 서로가 쥔 행을 기다리게 되기 때문이다. NFR-CON-04.

판매를 중단한 메뉴는 주문할 수 없다. 없는 메뉴 id와 같은 400 존재하지 않는 메뉴입니다를 받는다. 손님 화면에는 판매중 메뉴만 보이므로 이 경로는 화면이 오래된 목록을 들고 있을 때만 닿고, 손님 입장에서 둘은 구분되지 않는다.

현재 구현 메모. 응답이 봉투 없는 CreateResponse다. message, orderNumber, totalPrice를 맨 위에 담는다. 수량 위반 400과 검증 실패 400의 응답 형태가 서로 다르다. 수량 위반은 CreateResponse.rejected 모양이고, 검증 실패는 RsData 모양이다. 재고 부족 409는 RsData 모양이며 메시지에 메뉴 이름 대신 menuId가 담긴다. 손님에게 어떤 메뉴가 품절인지 이름으로 알리는 것은 손님 화면을 만드는 단계에서 정한다.

재고 행이 없는 메뉴를 주문하면 500이다. 400이나 409로 흡수하지 않는다. `docs/ERD.md` 3-4가 재고 없는 메뉴를 다루는 방법이 아니라 만들지 않는 방법을 규정했기 때문이다. 이제 메뉴 등록이 재고 행을 함께 만들므로 정상 흐름에서는 이 경로에 닿지 않는다. 남은 것은 엔티티를 우회한 쓰기와 운영자의 직접 삭제뿐이고, 그때 조용히 넘어가지 않고 드러나는 것이 이 500의 역할이다. 판단은 `docs/ADR/ADR-0003`에 있고, 재고 행 부재를 판정하는 자리는 `StockRepository.requireByMenuId` 하나다.

동시성이 보장된다. 마지막 한 잔을 두 손님이 같은 순간에 눌러도 정확히 한 명만 성립한다. NFR-CON-01. 어느 락으로 그렇게 하는지는 `cafekiosk.stock.lock-strategy` 프로퍼티가 고르고, 지금 값은 `pessimistic`과 `optimistic` 둘이다. 손님이 받는 응답은 어느 쪽이든 같다.

낙관적 락일 때만 한 가지가 더 있다. 버전 충돌로 롤백된 주문은 서버가 새 트랜잭션에서 다시 시도하므로, 손님 입장에서는 한 번 누른 요청이 그대로 성립하거나 재고 부족 409를 받는다. 재시도가 상한에 닿으면 409로 잠시 후 다시 시도해 달라는 메시지가 나가는데, 정상적인 경쟁에서는 닿지 않는 경로다. 재시도가 여러 번 돌아도 주문 행은 하나만 남는다. 앞선 시도가 만든 행은 롤백으로 사라지기 때문이다.

#### GET /api/order/{orderNumber}

손님이 받은 대기번호로 자기 주문의 상태를 조회한다. 인증하지 않는다. FR-KSK-06. 화면 SC-02가 이 응답으로 진행 막대를 그린다. 이 화면은 폴링하지 않는다. NFR-UX-04.

응답 200.

```json
{
  "resultCode": "200-1",
  "message": "주문 조회 성공",
  "data": {
    "orderNumber": "0007",
    "status": "IN_PROGRESS",
    "orderTime": "2026-07-23T14:05:00",
    "totalPrice": 14000,
    "items": [
      { "menuName": "에티오피아 예가체프", "orderPrice": 4500, "count": 2 },
      { "menuName": "브라질 산토스", "orderPrice": 5000, "count": 1 }
    ]
  }
}
```

status는 CONFIRMED, IN_PROGRESS, READY, COMPLETED, CANCELLED 다섯이다. FR-ORD-10. menuName과 orderPrice는 둘 다 주문 시점 스냅샷이며 현재 메뉴의 이름과 가격이 아니다. FR-ORD-05, FR-ORD-11. 그래서 판매를 중단한 메뉴가 섞인 과거 주문도 품목명과 금액까지 온전히 조회된다.

현재 구현 메모. 이 엔드포인트는 아직 없다. 지금은 대신 POST `/api/order/list`가 이메일을 본문으로 받아 그 이메일의 모든 주문을 돌려준다. 그런데 임의의 이메일로 남의 주문을 통째 조회할 수 있어 FR-KSK-09와 충돌한다. 목표는 대기번호로 자기 주문만 조회하는 이 경로로 대체하는 것이다. 아래는 현재의 list 엔드포인트다.

#### POST /api/order/list, 현재 구현 전용

이메일 기준으로 그 손님의 주문 내역을 조회한다. 근거는 `OrderController.orderList`. 목표에서는 위의 GET `/api/order/{orderNumber}`로 대체되어 사라진다.

요청

```json
{ "email": "guest@example.com" }
```

응답 200. 봉투 없는 OrderListResponse다.

```json
{
  "email": "guest@example.com",
  "orders": [
    {
      "orderId": 7,
      "orderNumber": "0007",
      "status": "CONFIRMED",
      "orderTime": "2026-07-23T14:05:00",
      "totalPrice": 14000,
      "items": [
        { "menuName": "에티오피아 예가체프", "orderPrice": 4500, "count": 2 }
      ]
    }
  ]
}
```

상태 코드. 200 성공, 404 해당 이메일의 주문이 없음.

### 4-4. 주방과 점주 주문 관리 orders

#### GET /api/orders

점주와 바리스타가 볼 전체 주문 목록이다. 목표는 점주 인증이다. FR-KIT-06. 근거는 `OrderController.getOrders`.

쿼리 파라미터. status는 선택이다. 지정하면 그 상태만, 생략하면 전체를 준다. 결과가 비어도 200과 빈 배열이다. FR-KIT-03. 정렬은 주문 시각 오름차순으로, 먼저 들어온 주문이 먼저다. FR-KIT-02.

예시. `GET /api/orders?status=IN_PROGRESS`

응답 200.

```json
{
  "resultCode": "200-1",
  "message": "주문 목록 조회 성공",
  "data": [
    {
      "orderId": 7,
      "orderNumber": "0007",
      "status": "IN_PROGRESS",
      "orderTime": "2026-07-23T14:05:00",
      "totalPrice": 14000,
      "items": [
        { "menuName": "에티오피아 예가체프", "orderPrice": 4500, "count": 2 }
      ]
    }
  ]
}
```

상태 코드. 200 항상, 400 status 값이 OrderStatus로 변환되지 않을 때. 이 엔드포인트는 이미 RsData로 감싸 내려온다.

#### PATCH /api/order/{orderId}/status

점주가 주문 상태를 다음 단계로 전이시킨다. 목표는 점주 인증이다. 근거는 `OrderController.changeStatus`, `OrderDto.ChangeStatusRequest`.

전이 규칙은 Order 엔티티가 소유한다. FR-ORD-04. 정상 흐름은 CONFIRMED, IN_PROGRESS, READY, COMPLETED다. FR-ORD-01. CONFIRMED와 IN_PROGRESS에서만 CANCELLED로 갈 수 있다. FR-ORD-02. 허용되지 않은 전이는 409로 거부되고 상태는 그대로다. FR-ORD-03.

CANCELLED로 전이하면 그 주문이 깎았던 재고를 되돌린다. FR-ORD-09, FR-STK-05. 이미 취소된 주문을 다시 취소하면 전이 자체가 409로 거부되므로 재고가 두 번 늘어나지 않는다. 복구도 차감과 같은 menuId 오름차순으로 접근하고, 같은 락 전략으로 행을 확보하며, 낙관적 락이면 주문 생성과 똑같이 재시도한다. 재고에 닿는 두 경로가 서로 다른 성질을 갖지 않게 하려는 것이다. 준비완료 이후에는 취소할 수 없으므로 다 만든 음료의 재고가 장부로 돌아오는 일도 없다.

요청

```json
{ "status": "IN_PROGRESS" }
```

응답 200.

```json
{ "resultCode": "200-1", "message": "주문 상태가 변경되었습니다.", "data": null }
```

상태 코드. 200 성공, 409 허용되지 않은 전이와 낙관적 락 재시도 소진, 400 없는 주문이나 잘못된 요청, 401 권한 없음.

현재 구현 메모. 응답이 봉투 없는 ChangeStatusResponse다. message만 담는다. 그리고 CORS 허용 메서드에 PATCH가 없어 브라우저가 이 엔드포인트를 직접 부르면 막힌다. 목표는 PATCH를 허용에 넣는 것이다.

### 4-5. 재고 stock

재고 조정은 구현돼 있다. 재고 조회 전용 엔드포인트는 없고, 남은 수량은 `GET /api/menu`가 메뉴 목록에 함께 실어 내려준다.

#### PATCH /api/stock/{menuId}

점주가 특정 메뉴의 재고를 조정한다. FR-ADM-02. 화면 SC-05 재고 탭이 쓴다.

요청

```json
{ "quantity": 50 }
```

응답 200.

```json
{ "resultCode": "200-1", "message": "재고를 조정하였습니다.", "data": { "menuId": 1, "quantity": 50 } }
```

상태 코드. 200 성공, 400 수량 누락이나 음수 요청, 401 권한 없음, 404 없는 메뉴이거나 판매가 중단된 메뉴.

동작. 재고 규칙은 Stock 엔티티가 소유한다. 증감은 increase와 decrease가, 절대값 조정은 adjustTo가 스스로 판단하고, 어떤 경로로도 재고는 음수가 되지 않는다. FR-STK-04, FR-STK-06.

quantity는 증감이 아니라 절대 수량이다. 점주가 아는 것은 몇 개 늘었는지가 아니라 지금 몇 개인지이기 때문이다. 델타로 받으면 화면이 현재 수량을 읽어 빼서 보내야 하는데, 읽은 순간과 보내는 순간 사이에 손님이 주문하면 그 계산이 틀린 값을 만든다.

판매가 중단된 메뉴는 없는 메뉴와 같은 404다. 재고 행은 남아 있지만 그 메뉴는 목록에도 안 뜨고 주문도 안 되므로 수량을 고쳐도 아무 일이 일어나지 않는다.

현재 구현 메모. 0은 정상 요청이고 품절 처리가 된다. 음수는 `StockDto.AdjustRequest`의 `@PositiveOrZero`가 먼저 걸러 400이 되고 `Stock.adjustTo`가 같은 판정을 한 겹 더 갖는다. 수량 필드를 아예 빠뜨린 요청도 400이다. `@NotNull`이 없으면 0으로 채워져 품절 처리로 조용히 성립하기 때문이다.

재고 행이 없는 메뉴에 이 엔드포인트를 부르면 만들어 주지 않고 500이다. 관리 API가 어긋난 데이터를 조용히 정상으로 바꾸면 어떤 경로가 그 상태를 만들었는지 영영 드러나지 않는다. `docs/ADR/ADR-0003`의 판단이 조정 경로에도 그대로 적용된다.

401은 아직 없다. 인증이 붙지 않아 지금은 누구나 이 엔드포인트를 부를 수 있다. Phase 3이다.

CORS 허용 메서드에 PATCH가 없는 것은 이 엔드포인트에 영향을 주지 않는다. 브라우저는 3000의 BFF 라우트를 부르고 그건 same-origin이라 프리플라이트가 없으며, BFF에서 8080으로 나가는 요청은 서버 간 호출이라 동일 출처 정책이 적용되지 않는다. CORS는 브라우저가 8080을 직접 부를 때만 문제가 된다.

### 4-6. 파일 업로드 upload

#### POST /api/upload/image

점주가 메뉴 이미지를 올린다. 목표는 점주 인증이다. FR-FILE-04. 근거는 `FileUploadController.uploadImage`.

요청. multipart form-data. 필드 이름은 file이다. 이미지만 허용하고 크기는 5MB 이하다. FR-FILE-02.

응답 200.

```json
{
  "imageUrl": "/uploads/3f9a1c2e-....jpg",
  "filename": "3f9a1c2e-....jpg",
  "originalFilename": "colombia.jpg"
}
```

저장 파일명은 UUID다. FR-FILE-03. 응답 imageUrl은 호스트 없는 상대경로다. 배포 주소가 DB에 박히지 않게 한다. FR-FILE-07.

상태 코드. 200 성공, 400 빈 파일이나 이미지가 아닌 파일이나 5MB 초과, 500 저장 중 오류. 오류 응답은 {message} 형태다.

현재 구현 메모. 응답이 RsData가 아니라 Map이다. imageUrl, filename, originalFilename을 맨 위에 담는다. 인증은 아직 없다.

## 5. BFF 프록시 계층

브라우저는 아래 Next.js 라우트를 부르고, BFF가 백엔드로 중계한다. 근거는 frontend/src/app/api 아래 라우트다. BFF는 요청을 1차 검증하고, 필드 이름을 백엔드에 맞게 바꾸고, 백엔드 오류를 {message} 하나로 정규화한다. NFR-UX-03.

| BFF 라우트 | 메서드 | 백엔드 대상 | 하는 일 |
| --- | --- | --- | --- |
| `/api/menu` | GET | GET `/api/menu` | 메뉴 목록 중계 |
| `/api/menu` | POST | POST `/api/menu` | 메뉴 등록, 필드 매핑 |
| `/api/menu/{menuId}` | PUT | PUT `/api/menu/modify/{menuId}` | 메뉴 수정, 필드 매핑 |
| `/api/menu/{menuId}` | DELETE | DELETE `/api/menu/delete/{menuId}` | 메뉴 삭제 |
| `/api/order` | POST | POST `/api/order` | 주문 생성 |
| `/api/order/history` | POST | POST `/api/order/list` | 주문 내역 조회 |
| `/api/stock/{menuId}` | PATCH | PATCH `/api/stock/{menuId}` | 재고 조정 |

재고 라우트는 아직 부르는 화면이 없다. 재고 조정은 관리자 기능이고 손님 화면에 관리자 기능을 새로 얹지 않기로 했으므로, 실제 사용은 관리자 화면이 생기는 Phase 3에서 시작된다.

### 5-1. 필드 매핑

브라우저가 보내는 이름과 백엔드가 받는 이름이 다르다. BFF가 사이에서 바꾼다.

| 브라우저 필드 | 백엔드 필드 | 대상 라우트 |
| --- | --- | --- |
| menu_name | menuName | POST `/api/menu` |
| image | imageURL | POST `/api/menu` |
| menu_name | menu_name | PUT `/api/menu/{menuId}` |
| image | img_url | PUT `/api/menu/{menuId}` |

### 5-2. BFF 검증

BFF는 백엔드에 닿기 전에 필수 필드와 값 범위를 먼저 본다. 메뉴 생성과 수정은 email, category, menu_name, price를 확인하고 가격 상한 10,000,000원을 검사한다. 주문 생성은 email과 items 존재, 총 수량 1개 이상 100개 이하를 검사한다. 주문 내역 조회는 email을 확인한다. 재고 조정은 quantity가 0 이상의 정수인지 확인한다. 이 검사는 백엔드 검증을 대신하지 않는다. 백엔드가 최종 판단한다.

재고 조정에서 0을 falsy로 걸러 내지 않는 것이 중요하다. 0은 품절 처리를 뜻하는 정상 값이라 `!body.quantity` 형태로 검사하면 품절 처리가 통째로 400이 되고, 그 사실이 오류 메시지에 드러나지도 않는다.

현재 구현 메모. 목표에서 BFF는 로그인 응답의 토큰을 httpOnly 쿠키로 심고, 점주 요청에 그 쿠키를 실어 백엔드로 보낸다. FR-AUTH-08. 지금은 인증 흐름이 없어 BFF가 토큰을 다루지 않는다. 또한 모든 라우트가 백엔드 주소를 하드코딩한다.

## 6. 현재 구현과 목표의 차이 요약

이 문서는 완성 기준으로 적었다. 아래는 지금 코드가 목표에 아직 못 미치는 지점을 모은 것이다. 진행 단계의 정본은 `docs/REQUIREMENTS.md` 10절이다.

| 주제 | 목표 | 현재 | 단계 |
| --- | --- | --- | --- |
| 동시성 | 마지막 한 잔 정확히 한 명 성공 | 락 없음 | Phase 2 |
| 인증 | JWT와 httpOnly 쿠키, ROLE_OWNER 보호 | 없음, 본문 이메일 비교 | Phase 3 |
| 인가 필드 | 서버 신원 기반, 본문에서 email 제거 | 메뉴 수정과 삭제 본문에 email | Phase 3 |
| 손님 주문 조회 | 대기번호로 자기 주문만 | 이메일로 통째 조회 | Phase 3 |
| 응답 봉투 | RsData로 통일 | 평문, 맨 DTO, Map 혼재 | Phase 3 |
| CORS PATCH | 허용 메서드에 PATCH | 누락 | Phase 3 |
| 백엔드 주소 | 환경 변수 | BFF 하드코딩 | Phase 3 |
