# Frontend (Next.js)

Next.js 16 App Router / React 19 / TypeScript 5 / Tailwind CSS 4.
ESLint 9 flat config (`npm run lint`). Prettier는 없다.

```bash
npm run dev    # 3000. 백엔드가 8080에 떠 있어야 메뉴/주문이 동작한다
```

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

현재 핸들러: `api/menu/route.ts`, `api/menu/[menuId]/route.ts`, `api/order/route.ts`, `api/order/history/route.ts`, `api/products/route.ts`

## 백엔드 주소가 하드코딩되어 있다

`http://localhost:8080`이 각 Route Handler와 `next.config.ts`의 `/uploads/*` rewrite에 직접 박혀 있다. 환경변수화는 아직 안 됐다. 새 핸들러를 추가할 땐 기존 패턴을 따르되, 이게 부채라는 건 알고 있을 것.

## ⚠️ `src/app/page.tsx`는 1200줄이 넘는 단일 컴포넌트다

키오스크 UI 전체가 하나의 `"use client"` 컴포넌트에 들어 있다. 컴포넌트 분리가 안 돼 있다.

여기를 수정할 땐 **전체를 다시 쓰지 말고 해당 영역만 국소적으로 고친다.** 리팩터링(컴포넌트 분리)은 그 자체로 별도 작업이지, 다른 변경에 끼워 넣을 일이 아니다.

## 검증

CI는 백엔드 테스트만 돌린다. **프론트엔드는 자동으로 검증되지 않는다.**
UI를 바꿨으면 `npm run dev`로 직접 확인하고, PR에 Before/After 스크린샷을 첨부한다 (PR 템플릿 요구사항).
