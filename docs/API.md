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
| 401 | 401-1 | 점주 API 를 토큰 없이 또는 위조되거나 만료된 토큰으로 호출, 로그인 실패 | AuthenticationException, InvalidCredentialsException |
| 404 | 404-1 | 대상 데이터 없음 | NoSuchElementException |
| 409 | 409-1 | 허용되지 않은 주문 상태 전이, 재고 부족, 낙관적 락 재시도 소진 | InvalidOrderStatusTransitionException, OutOfStockException, OptimisticLockingFailureException |
| 503 | 503-1 | 분산 락 대기 한계 초과 | StockLockTimeoutException |

401 은 두 갈래이고 메시지가 다르다. 토큰 문제는 로그인이 필요합니다이고 `WWW-Authenticate: Bearer` 헤더가 함께 나간다. 토큰이 깨졌으면 그 헤더에 invalid_token 오류 코드가 붙는다. 로그인 실패는 이메일 또는 비밀번호가 올바르지 않습니다다. 토큰 문제는 보안 필터에서 터져 DispatcherServlet 을 거치지 않으므로, `global/security/RsDataAuthenticationEntryPoint` 가 MVC 의 예외 리졸버로 넘겨 이 핸들러가 봉투를 쓴다.

### 2-3. 인증

목표 인증은 stateless JWT다. 서명은 HS256, 키는 32바이트 이상이다. FR-AUTH-03, NFR-SEC-05. 점주가 로그인하면 Access 토큰을 받고, 토큰은 BFF가 httpOnly 쿠키로 보관한다. 브라우저 localStorage에 두지 않는다. FR-AUTH-08. 점주 전용 API를 토큰 없이 부르면 401이다. FR-AUTH-04. 손님 엔드포인트는 인증을 요구하지 않는다. FR-AUTH-06. 역할은 ROLE_OWNER 하나다. FR-AUTH-07.

인가는 서버가 검증한 신원에 근거한다. 요청 본문의 이메일을 비교해 권한을 판단하지 않는다. FR-AUTH-05, NFR-SEC-06.

토큰은 이 서버가 직접 발급하고 Spring Security 리소스 서버가 검증한다. sub 는 점주 이메일이고 role 클레임에 ROLE_OWNER 가 실리며 수명은 12시간이다. 리프레시 토큰이 없으므로 수명이 곧 다시 로그인하는 주기다. 서명 키는 환경 변수 JWT_SECRET 에서만 읽는다. 판단은 `docs/ADR/ADR-0006` 에 있다.

경로별 권한은 `global/security/SecurityConfig` 한곳이 정하고 잠긴 것이 기본이다. 3절 표에서 인증 없음인 경로와 `/uploads/**`, Swagger 만 열려 있고 나머지 경로는 전부 ROLE_OWNER 다. 손님 엔드포인트를 새로 만들면 공개 목록에 메서드까지 맞춰 더해야 한다. 잊으면 401 이다.

현재 구현 메모. 백엔드 인증이 붙었다. 점주 API 를 토큰 없이 부르면 401 이다. 남은 것은 BFF 하나다. BFF 가 아직 토큰을 다루지 않아, 지금 화면의 메뉴 등록, 수정, 삭제, 이미지 업로드는 토큰 없이 백엔드를 불러 401 을 받는다.

### 2-4. CORS

`global/config/WebConfig.java`가 CORS를 연다. 허용 출처는 `http://localhost:3000`, 자격 증명 허용, maxAge 3600이다.

현재 구현 메모. 허용 메서드가 GET, POST, PUT, DELETE, OPTIONS다. PATCH가 빠져 있어 브라우저에서 주문 상태 변경 PATCH를 직접 부르면 CORS로 막힌다. 목표는 PATCH를 허용 메서드에 넣는 것이다.

## 3. 엔드포인트 요약

| 메서드 | 경로 | 인증 | 하는 일 | FR |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/login` | 없음 | 점주 로그인, 토큰 발급 | FR-AUTH-01 |
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

로그아웃은 표에 없다. 백엔드 엔드포인트를 두지 않고 BFF 가 쿠키를 지우는 것으로 성립한다. 이유는 4-1 에 있다.

## 4. 도메인별 상세

### 4-1. 인증 auth

엔드포인트는 로그인 하나다. 로그아웃은 백엔드에 두지 않는다.

#### POST /api/auth/login

점주가 이메일과 비밀번호로 로그인한다. FR-AUTH-01. 근거는 `AuthController.login`, `AuthService.login`, `AuthDto`.

요청

```json
{
  "email": "owner@cafe.com",
  "password": "비밀번호"
}
```

응답 200. 토큰은 `data.accessToken` 에 실린다. 이 응답을 받는 것은 BFF 다. BFF 가 토큰을 꺼내 httpOnly 쿠키로 심고 나머지만 브라우저로 넘긴다. 브라우저는 토큰 본문을 보지 못한다. 응답 헤더가 아니라 본문에 싣는 것은 OAuth2 토큰 응답의 관례를 따른 것이고, 필드로 두면 타입과 Swagger 스키마가 함께 남는다.

```json
{
  "resultCode": "200-1",
  "message": "로그인되었습니다.",
  "data": { "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "email": "owner@cafe.com", "role": "ROLE_OWNER" }
}
```

상태 코드. 200 성공, 400 이메일이나 비밀번호 누락, 401 인증 실패. 실패 메시지는 이메일과 비밀번호 중 어느 쪽이 틀렸는지 알리지 않는다. 계정 존재 여부가 새지 않게 한다. 화면 SC-03. 걸리는 시간도 맞춘다. 없는 이메일에도 미리 만든 해시와 비교를 한 번 치르므로 응답 시간으로도 계정이 있는지 드러나지 않는다.

#### 로그아웃, 백엔드 엔드포인트 없음

로그아웃은 BFF 가 쿠키를 지우는 것으로 성립한다. FR-AUTH-09. 백엔드에는 엔드포인트를 두지 않는다.

토큰이 stateless 라 서버에는 지울 상태가 없다. 200 만 돌려주는 엔드포인트를 두면 토큰이 폐기된 것처럼 보이는데 실제로는 아무 일도 일어나지 않는다. 점주 전용으로 두면 만료된 토큰을 든 채 로그아웃을 누를 때 로그아웃이 401 로 실패하기까지 한다.

대가로 쿠키를 지워도 이미 복사된 토큰은 만료 시각까지 유효하다. 리프레시 토큰을 두지 않은 FR-AUTH-10 과 같은 선택의 대가다. 되돌리려면 서버가 폐기한 토큰 목록을 들고 있어야 하고, 그 순간 stateless 가 아니게 된다.

### 4-2. 메뉴 menu

#### GET /api/menu

판매중인 메뉴 목록을 조회한다. 익명을 포함해 누구나 부른다. FR-MNU-01. 근거는 `MenuController.getMenus`, `MenuService.findAllWithStock`, `MenuDto.MenuListResponse`.

판매를 중단한 메뉴는 이 목록에서 빠진다. FR-MNU-07. 행은 남아 있지만 `deleted_at`이 채워진 메뉴는 `MenuRepository.findAllByDeletedAtIsNullOrderByIdAsc`가 걸러낸다.

목록은 menu_id 오름차순이다. 등록한 순서이고 새 메뉴는 끝에 붙는다. 정렬 기준이 없던 때는 메뉴를 수정하면 그 메뉴가 목록 끝으로 밀렸다. PostgreSQL 은 UPDATE 때 행의 새 버전을 테이블 뒤쪽에 쓰고, 정렬 없는 조회는 저장된 순서대로 돌려주기 때문이다. 손님 화면은 받은 순서를 그대로 그린다.

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

메뉴를 등록한다. 점주 토큰이 필요하다. FR-MNU-02. 가격은 0원 이상 10,000,000원 이하다. FR-MNU-03. 근거는 `MenuController.createMenu`, `CreateMenuRequestDto`.

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

등록자는 요청 본문이 아니라 토큰에서 온다. 토큰의 sub 가 점주 이메일이라 `MenuController.createMenu` 가 `Authentication.getName()` 을 등록자로 넘긴다. 그래서 요청 본문에 email 필드가 없다. FR-MNU-06, SC-05.

현재 구현 메모. 응답이 지금은 평문 문자열 생성 완료되었습니다이며 RsData가 아니다. 가격 범위를 벗어난 등록의 400도 평문이다. 판정의 주인은 `Menu`지만 등록 컨트롤러가 실패 응답을 지금의 모양으로 지키려고 같은 범위를 앞단에서 먼저 보기 때문이다. 응답을 RsData로 통일할 때 그 앞단 검사를 걷어낸다.

#### PUT /api/menu/modify/{id}

메뉴의 이름, 가격, 이미지, 분류를 수정한다. 점주 토큰이 필요하다. 가격은 등록과 같이 0원 이상 10,000,000원 이하이고, 빠지면 400이다. FR-MNU-03. 근거는 `MenuController.modifyMenu`, `MenuService.modify`, `MenuDto.MenuModifyRequest`.

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

상태 코드. 200 수정 완료, 400 검증 실패, 401 토큰 없음, 404 없거나 판매 중단된 메뉴.

누가 고칠 수 있는지는 점주 토큰으로만 가른다. 메뉴의 등록자와 비교하지 않으므로 다른 점주가 등록한 메뉴도 고칠 수 있다. FR-AUTH-05, FR-MNU-06.

가격 범위는 `Menu` 엔티티가 판정한다. 등록과 수정이 같은 판정을 지나므로 두 경로의 규칙이 갈리지 않는다. 한때 등록은 컨트롤러가, 수정은 DTO의 `@Positive`가 따로 보다가 수정만 0원을 거절하고 상한을 넘겼다. 범위를 벗어나면 엔티티가 던진 예외를 `GlobalExceptionHandler`가 400-1로 옮긴다.

#### DELETE /api/menu/delete/{menu_id}

메뉴를 삭제한다. 점주 토큰이 필요하고 요청 본문은 없다. 근거는 `MenuController.deleteMenu`, `MenuService.deleteMenu`.

**행을 지우지 않고 판매를 중단한다.** FR-MNU-07. `menu.deleted_at`에 시각을 찍는 UPDATE 한 번이 나가고, 그 뒤로 이 메뉴는 목록 조회와 수정과 주문에서 전부 빠진다. 과거 주문은 이 메뉴를 그대로 참조한다.

하드 삭제를 버린 이유는 `order_item.menu_id`와 `stock.menu_id`가 이 행을 참조하기 때문이다. 참조가 있는 메뉴는 외래키 위반으로 지워지지 않고, 참조를 끊고 지우면 과거 주문이 무엇을 팔았는지 알 수 없게 된다. 자세한 판단은 `docs/ERD.md` 3-3에 있다.

응답 200.

```json
{ "resultCode": "200-1", "message": "삭제되었습니다.", "data": null }
```

상태 코드. 200 삭제, 401 토큰 없음, 404 없거나 판매 중단된 메뉴.

누가 지울 수 있는지는 수정과 같이 점주 토큰으로만 가른다. 등록자와 비교하지 않는다. FR-AUTH-05.

이미 판매를 중단한 메뉴를 다시 부르면 404다. 조회에서 걸러지므로 없는 메뉴와 구분되지 않는다. 메뉴 수정과 재고 조정도 판매 중단된 메뉴를 같은 404로 다룬다. 이 경우가 예전에는 401이었다. 본문 이메일이 등록자와 다를 때와 한 갈래로 묶여 있었기 때문이다. 이메일 비교를 걷어내자 실패할 이유가 대상이 없는 것 하나만 남았다. 토큰을 통과한 요청에 401을 주면 다시 로그인하라는 말이 되는데 다시 로그인해도 없는 메뉴는 그대로이므로 404로 바꿨다.

이미 판매 중단된 메뉴에 200을 주는 멱등 삭제도 저울에 올렸다. 버린 이유는 판매 중단된 메뉴를 없는 메뉴로 다루는 규칙이 수정과 재고 조정에 이미 있어서다. 삭제만 판매 중단된 메뉴를 알아보게 하면 그 메뉴를 찾는 조회가 하나 더 생기고 세 경로의 규칙이 갈린다.

현재 구현 메모. 성공 응답은 평문 문자열 삭제되었습니다이며 RsData가 아니다. 소프트 삭제로 바뀐 뒤에도 이 문자열을 바꾸지 않았다. 겉으로 드러나는 계약은 그대로 두고 안쪽 메커니즘만 바꾸는 편이 BFF와 프론트를 건드리지 않아서다.

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

상태 코드. 200 성공, 400 수량 위반이나 검증 실패나 없는 메뉴, 409 재고 부족과 낙관적 락 재시도 소진, 503 분산 락 대기 한계 초과, 500 재고 행이 없는 메뉴.

주문 생성이 아이템 수량만큼 재고를 차감한다. FR-STK-02. 재고가 부족하면 주문 전체를 실패시키고 409를 준다. 부분 차감은 없다. FR-STK-03. 주문 생성과 재고 차감은 한 트랜잭션에서 전부 성공하거나 전부 실패한다. FR-ORD-08. 대기번호는 주문 PK에서 파생하며 전역 유일하고 단조 증가한다. FR-ORD-07. 각 아이템은 주문 시점의 가격과 이름을 스냅샷으로 굳힌다. FR-ORD-05, FR-ORD-11.

재고에 닿는 순서는 menuId 오름차순으로 고정한다. 요청 본문의 아이템 순서와 무관하다. 락이 붙은 지금은 이 정렬이 실제로 데드락을 막는다. 두 손님이 같은 두 메뉴를 반대 순서로 담으면 서로가 쥔 행을 기다리게 되기 때문이다. NFR-CON-04.

판매를 중단한 메뉴는 주문할 수 없다. 없는 메뉴 id와 같은 400 존재하지 않는 메뉴입니다를 받는다. 손님 화면에는 판매중 메뉴만 보이므로 이 경로는 화면이 오래된 목록을 들고 있을 때만 닿고, 손님 입장에서 둘은 구분되지 않는다.

현재 구현 메모. 응답이 봉투 없는 CreateResponse다. message, orderNumber, totalPrice를 맨 위에 담는다. 수량 위반 400과 검증 실패 400의 응답 형태가 서로 다르다. 수량 위반은 CreateResponse.rejected 모양이고, 검증 실패는 RsData 모양이다. 재고 부족 409는 RsData 모양이며 메시지에 메뉴 이름 대신 menuId가 담긴다. 손님에게 어떤 메뉴가 품절인지 이름으로 알리는 것은 손님 화면을 만드는 단계에서 정한다.

재고 행이 없는 메뉴를 주문하면 500이다. 400이나 409로 흡수하지 않는다. `docs/ERD.md` 3-4가 재고 없는 메뉴를 다루는 방법이 아니라 만들지 않는 방법을 규정했기 때문이다. 이제 메뉴 등록이 재고 행을 함께 만들므로 정상 흐름에서는 이 경로에 닿지 않는다. 남은 것은 엔티티를 우회한 쓰기와 운영자의 직접 삭제뿐이고, 그때 조용히 넘어가지 않고 드러나는 것이 이 500의 역할이다. 판단은 `docs/ADR/ADR-0003`에 있고, 재고 행 부재를 판정하는 자리는 `StockRepository.requireByMenuId` 하나다.

동시성이 보장된다. 마지막 한 잔을 두 손님이 같은 순간에 눌러도 정확히 한 명만 성립한다. NFR-CON-01. 어느 락으로 그렇게 하는지는 `cafekiosk.stock.lock-strategy` 프로퍼티가 고르고, 지금 값은 `pessimistic`과 `optimistic`과 `distributed` 셋이다. NFR-CON-03이 요구한 세 전략이 전부 갖춰졌다. 손님이 받는 응답은 어느 쪽이든 같다.

같은 이메일로 첫 주문이 동시에 들어와도 모두 성립한다. 손님 행은 하나만 생기고 주문은 전부 그 아래에 붙는다. NFR-CON-06.

낙관적 락일 때만 한 가지가 더 있다. 버전 충돌로 롤백된 주문은 서버가 새 트랜잭션에서 다시 시도하므로, 손님 입장에서는 한 번 누른 요청이 그대로 성립하거나 재고 부족 409를 받는다. 재시도가 상한에 닿으면 409로 잠시 후 다시 시도해 달라는 메시지가 나가는데, 정상적인 경쟁에서는 닿지 않는 경로다. 재시도가 여러 번 돌아도 주문 행은 하나만 남는다. 앞선 시도가 만든 행은 롤백으로 사라지기 때문이다.

분산 락일 때만 있는 것도 하나다. 503이다. 그 메뉴의 열쇠를 대기 한계 안에 받지 못하면 나간다. 요청이 잘못된 것도 재고가 모자란 것도 아니고 앞에 줄이 길어 순서를 못 받은 것이다. 409와 나누는 이유가 거기 있다. 409는 자원의 현재 상태와 부딪힌 것이라 그 상태가 바뀌지 않는 한 다시 눌러도 같은 답이지만, 503은 자원에 닿아 보지도 못했고 줄이 짧아지면 통과한다. 손님이 할 일은 잠시 후 다시 누르는 것 하나다. 열쇠를 놓는 시점은 트랜잭션이 끝난 뒤이고 커밋이든 롤백이든 같다. 재고 부족으로 거절된 주문이 열쇠를 쥔 채로 남지 않는다.

#### GET /api/order/{orderNumber}

손님이 받은 대기번호로 그 주문의 상태를 조회한다. 인증하지 않는다. FR-KSK-06. 근거는 `OrderController.getOrderByNumber`, `OrderService.getOrderByNumber`, `OrderDto.OrderLookupResponse`. 화면 SC-02가 이 응답으로 진행 막대를 그린다. 이 화면은 폴링하지 않는다. NFR-UX-04.

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

상태 코드. 200 성공, 404 그 대기번호의 주문이 없음. 번호를 0으로 채워 맞춰 주지 않으므로 `7`처럼 형식이 다른 번호도 없는 번호와 같은 404다. 번호의 형식은 `Order.assignOrderNumber` 한곳이 정한다.

status는 CONFIRMED, IN_PROGRESS, READY, COMPLETED, CANCELLED 다섯이다. FR-ORD-10. menuName과 orderPrice는 둘 다 주문 시점 스냅샷이며 현재 메뉴의 이름과 가격이 아니다. FR-ORD-05, FR-ORD-11. 그래서 판매를 중단한 메뉴가 섞인 과거 주문도 품목명과 금액까지 온전히 조회된다.

응답에 손님 이메일도 내부 주문 id인 orderId도 싣지 않는다. 인증 없이 열린 응답이라 누가 주문했는지가 실리면 안 되고, orderId는 점주 API인 상태 전이가 주문을 집는 손잡이라 손님 계약에 섞지 않는다. 그래서 주방용 `OrderSummary`와 필드가 거의 같아도 타입을 따로 둔다. 한 타입을 같이 쓰면 주방 쪽에 필드를 더하는 순간 이 응답에도 실린다.

대기번호를 맞혀 볼 수 있다는 한계는 알고 받아들였다. 대기번호는 주문 PK에서 파생한 순번이다. 0001부터 차례로 부르면 모든 주문의 품목과 금액과 시각이 보이고, 속도 제한도 없어 전부 훑으면 하루 주문 수와 매출이 드러난다. 그래도 인증 없이 여는 것은 응답이 사람을 가리키지 않기 때문이다. 한때 있던 이메일 조회는 이메일 하나로 한 사람의 주문 이력을 통째 묶어 내주었고, 이 경로에서는 그 묶음이 끊긴다. 드러나는 것은 픽업대 화면에 뜨는 번호와 상태에 품목과 금액이 더해진 정도다. 그것까지 막으려면 순번이 아닌 조회용 값을 주문 생성 때 따로 발급해야 한다. 판단은 `docs/ADR/ADR-0007`에 있다.

### 4-4. 주방과 점주 주문 관리 orders

#### GET /api/orders

점주와 바리스타가 볼 전체 주문 목록이다. 점주 토큰이 필요하다. FR-KIT-06. 근거는 `OrderController.getOrders`.

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

상태 코드. 200 항상, 400 status 값이 OrderStatus로 변환되지 않을 때, 401 토큰 없음. 이 엔드포인트는 이미 RsData로 감싸 내려온다.

#### PATCH /api/order/{orderId}/status

점주가 주문 상태를 다음 단계로 전이시킨다. 점주 토큰이 필요하다. 근거는 `OrderController.changeStatus`, `OrderDto.ChangeStatusRequest`.

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

상태 코드. 200 성공, 409 허용되지 않은 전이와 낙관적 락 재시도 소진, 503 분산 락 대기 한계 초과, 400 없는 주문이나 잘못된 요청, 401 권한 없음. 취소 전이가 재고를 되돌리며 주문 생성과 같은 전략으로 행을 확보하므로, 재고에 닿는 두 경로가 같은 상태 코드를 갖는다.

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

점주 토큰이 필요하다. 토큰 없이 부르면 401 이다. 재고 행이 없을 때 일부러 내는 500 도 토큰을 통과한 뒤의 일이다.

CORS 허용 메서드에 PATCH가 없는 것은 이 엔드포인트에 영향을 주지 않는다. 브라우저는 3000의 BFF 라우트를 부르고 그건 same-origin이라 프리플라이트가 없으며, BFF에서 8080으로 나가는 요청은 서버 간 호출이라 동일 출처 정책이 적용되지 않는다. CORS는 브라우저가 8080을 직접 부를 때만 문제가 된다.

### 4-6. 파일 업로드 upload

#### POST /api/upload/image

점주가 메뉴 이미지를 올린다. 점주 토큰이 필요하다. FR-FILE-04. 근거는 `FileUploadController.uploadImage`.

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

상태 코드. 200 성공, 400 빈 파일이나 이미지가 아닌 파일이나 5MB 초과, 401 토큰 없음, 500 저장 중 오류. 오류 응답은 {message} 형태다. 401 만 보안 필터가 내므로 RsData 봉투다.

현재 구현 메모. 응답이 RsData가 아니라 Map이다. imageUrl, filename, originalFilename을 맨 위에 담는다. 인증은 붙었지만 지금 화면은 BFF 를 건너뛰고 8080 을 토큰 없이 직접 부르므로 화면의 업로드는 401 을 받는다. 업로드가 BFF 를 거치게 바뀔 때 함께 풀린다.

## 5. BFF 프록시 계층

브라우저는 아래 Next.js 라우트를 부르고, BFF가 백엔드로 중계한다. 근거는 frontend/src/app/api 아래 라우트다. BFF는 요청을 1차 검증하고, 필드 이름을 백엔드에 맞게 바꾸고, 백엔드 오류를 {message} 하나로 정규화한다. NFR-UX-03.

| BFF 라우트 | 메서드 | 백엔드 대상 | 하는 일 |
| --- | --- | --- | --- |
| `/api/menu` | GET | GET `/api/menu` | 메뉴 목록 중계 |
| `/api/menu` | POST | POST `/api/menu` | 메뉴 등록, 필드 매핑 |
| `/api/menu/{menuId}` | PUT | PUT `/api/menu/modify/{menuId}` | 메뉴 수정, 필드 매핑 |
| `/api/menu/{menuId}` | DELETE | DELETE `/api/menu/delete/{menuId}` | 메뉴 삭제 |
| `/api/order` | POST | POST `/api/order` | 주문 생성 |
| `/api/order/history` | POST | GET `/api/order/{orderNumber}` | 대기번호로 주문 조회, 본문의 번호를 경로로 옮긴다 |
| `/api/stock/{menuId}` | PATCH | PATCH `/api/stock/{menuId}` | 재고 조정 |

주문 조회 라우트의 이름이 history인 것은 이메일로 주문 내역을 통째 조회하던 때의 이름이다. 브라우저는 대기번호를 본문에 담아 POST로 보내고, BFF가 그 번호를 백엔드 경로로 옮겨 GET으로 중계한다. 라우트 모양은 손님 주문 화면을 만드는 단계에서 다시 정한다.

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

BFF는 백엔드에 닿기 전에 필수 필드와 값 범위를 먼저 본다. 메뉴 생성과 수정은 category, menu_name, price를 확인하고 가격 상한 10,000,000원을 검사한다. 주문 생성은 email과 items 존재, 총 수량 1개 이상 100개 이하를 검사한다. 주문 조회는 대기번호가 숫자로만 이뤄졌는지 확인한다. 재고 조정은 quantity가 0 이상의 정수인지 확인한다. 이 검사는 백엔드 검증을 대신하지 않는다. 백엔드가 최종 판단한다.

재고 조정에서 0을 falsy로 걸러 내지 않는 것이 중요하다. 0은 품절 처리를 뜻하는 정상 값이라 `!body.quantity` 형태로 검사하면 품절 처리가 통째로 400이 되고, 그 사실이 오류 메시지에 드러나지도 않는다.

주문 조회의 숫자 검사는 편의가 아니라 방어다. 대기번호가 백엔드 URL의 경로 한 칸이 되므로, `../orders` 같은 값을 그대로 이어 붙이면 fetch가 경로를 정규화해 다른 백엔드 경로를 부른다. 지금은 그 경로가 토큰 없이 401로 막히지만, BFF가 점주 토큰을 싣기 시작하면 손님 라우트가 점주 API를 대신 불러 주는 통로가 된다. `encodeURIComponent`는 점을 이스케이프하지 않으므로 이 검사를 대신하지 못한다.

현재 구현 메모. 목표에서 BFF는 로그인 응답의 토큰을 httpOnly 쿠키로 심고, 점주 요청마다 쿠키에서 꺼낸 토큰을 Authorization 헤더에 Bearer 로 실어 백엔드로 보낸다. FR-AUTH-08. 백엔드는 쿠키를 읽지 않는다. 지금은 BFF가 토큰을 다루지 않는다. 백엔드가 점주 API 를 잠갔으므로 메뉴 등록, 수정, 삭제와 재고 조정 라우트는 백엔드의 401 을 그대로 브라우저로 돌려준다. 모든 라우트가 백엔드 상태 코드를 그대로 넘기기 때문이다. 또한 모든 라우트가 백엔드 주소를 하드코딩한다.

## 6. 현재 구현과 목표의 차이 요약

이 문서는 완성 기준으로 적었다. 아래는 지금 코드가 목표에 아직 못 미치는 지점을 모은 것이다. 진행 단계의 정본은 `docs/REQUIREMENTS.md` 10절이다.

| 주제 | 목표 | 현재 | 단계 |
| --- | --- | --- | --- |
| 인증 | JWT와 httpOnly 쿠키, ROLE_OWNER 보호 | 백엔드는 토큰 검증과 401 까지, BFF 쿠키는 아직 | Phase 3 |
| 응답 봉투 | RsData로 통일 | 평문, 맨 DTO, Map 혼재 | Phase 3 |
| CORS PATCH | 허용 메서드에 PATCH | 누락 | Phase 3 |
| 백엔드 주소 | 환경 변수 | BFF 하드코딩 | Phase 3 |
