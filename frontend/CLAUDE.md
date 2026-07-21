# Frontend (Next.js)

Next.js 16 App Router / React 19 / TypeScript 5 / Tailwind CSS 4.
ESLint 9 flat config (`npm run lint`). Prettier는 없다.

```bash
npm run dev    # 3000. 백엔드가 8080에 떠 있어야 메뉴/주문이 동작한다
```

## 이 앱은 매장 카페 키오스크다

주소·우편번호 입력과 "다음 날 배송을 시작합니다" 문구는 부트캠프 팀 프로젝트 시절의 배송몰 잔재였고, **Phase 0에서 제거했다.** 새 코드에 배송·주소 개념을 넣지 않는다.

손님은 이메일만 넣고 주문하면 **대기번호**(`orderNumber`)를 받아 카운터에서 음료를 받아간다. 주문 내역의 가격은 **주문 시점 스냅샷**(`orderPrice`)이지 현재 메뉴 가격이 아니다 — 백엔드가 그렇게 내려준다.

제품 방향("왜")은 [`docs/PRODUCT.md`](../docs/PRODUCT.md), 로드맵·진행 상태는 [`docs/ROADMAP.md`](../docs/ROADMAP.md)에 있다.

## `/` 하나에 세 제품이 섞여 있다 — 곧 쪼개진다

지금은 **손님용 상품 목록에 메뉴 추가·수정·삭제 버튼이 그대로 노출**돼 있고, 삭제 권한 확인이 `window.prompt("삭제 권한 확인을 위해 이메일을 입력해주세요")`다. 로그인도 세션도 없다.

Phase 1에서 세 라우트로 분리된다:

| 경로 | 쓰는 사람 |
| --- | --- |
| `/` | 손님 — 메뉴 고르고 주문 → 대기번호 |
| `/kitchen` | 바리스타 — 들어온 주문을 `제조중 → 준비완료`로 |
| `/admin` | 점주 — 메뉴 CRUD + 재고 |

**관리자 기능을 손님 화면에 새로 추가하지 않는다.** 화면 분리는 UI 정리가 아니라 권한 경계를 만드는 일이다.

## ⚠️ `src/app/api/*`는 페이지용 API가 아니라 백엔드 프록시(BFF)다

브라우저는 8080을 직접 호출하지 않는다. 항상 이 Route Handler를 거친다:

```
브라우저 → /api/order (Route Handler) → http://localhost:8080/api/order
```

Route Handler가 하는 일:
- **입력 검증** — 예: `api/order/route.ts`는 주문 총 수량을 1~100개로 제한한다
- **필드명 변환** — 일부 핸들러는 프론트의 snake_case를 백엔드의 camelCase로 바꿔서 넘긴다 (`api/menu/route.ts`의 `menu_name` → `menuName`). 반면 `api/order/route.ts`는 camelCase를 그대로 통과시킨다. **핸들러마다 다르니 고치기 전에 해당 파일을 읽을 것.**
- **에러 메시지 정규화** — 백엔드 응답의 `message` / `msg` / `error` 중 아무거나 골라 `{ message }` 형태로 통일

**백엔드 API를 바꾸면 여기도 같이 고쳐야 한다.** 백엔드만 고치고 끝내면 프론트가 조용히 깨진다.

현재 핸들러: `api/menu/route.ts`, `api/menu/[menuId]/route.ts`, `api/order/route.ts`, `api/order/history/route.ts`

> 죽은 mock 라우트였던 `api/products/route.ts`와 그 잔재인 `Product.subtitle`은 **Phase 0에서 삭제했다.**

**라우트 핸들러의 `params`는 `Promise`다** (Next 16). `context: { params: Promise<{ menuId: string }> }`로 받고 `await` 한다 — `api/menu/[menuId]/route.ts` 참고.

**BFF 패턴이 한 군데 깨져 있다** — 이미지 업로드(`page.tsx:285`, `page.tsx:1003`)는 Route Handler를 거치지 않고 **브라우저에서 `localhost:8080`을 직접 호출한다.** 백엔드 CORS 설정에 의존하고 있는 상태다.

## 백엔드 주소가 하드코딩되어 있다

`http://localhost:8080`이 **9곳**에 직접 박혀 있다 — 각 Route Handler, `next.config.ts`의 `/uploads/*` rewrite, 그리고 위의 이미지 업로드 두 곳. `process.env`/`NEXT_PUBLIC_*` 사용은 0회다.

환경변수화(`NEXT_PUBLIC_API_BASE_URL`)는 **Phase 4**에서 한다. 그전까진 새 핸들러를 추가할 때 기존 패턴을 따르되, 이게 부채라는 건 알고 있을 것.

## ⚠️ `src/app/page.tsx`는 1200줄이 넘는 단일 컴포넌트다

키오스크 UI 전체가 하나의 `"use client"` 컴포넌트에 들어 있다. 컴포넌트 분리가 안 돼 있다.

여기를 수정할 땐 **전체를 다시 쓰지 말고 해당 영역만 국소적으로 고친다.** 리팩터링(컴포넌트 분리)은 그 자체로 별도 작업이지, 다른 변경에 끼워 넣을 일이 아니다.

## 검증

CI는 백엔드 테스트만 돌린다. **프론트엔드는 자동으로 검증되지 않는다.**
UI를 바꿨으면 `npm run dev`로 직접 확인하고, PR에 Before/After 스크린샷을 첨부한다 (PR 템플릿 요구사항).
