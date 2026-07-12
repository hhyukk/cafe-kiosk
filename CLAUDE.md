# cafe-kiosk

카페 키오스크 프로젝트. 백엔드(Spring Boot)와 프론트엔드(Next.js)가 한 레포에 있다.

## 레포 지도

| 경로 | 내용 |
| --- | --- |
| `Backend/App/` | Spring Boot 4 / Java 21 API 서버 (8080). **Gradle 루트가 여기다** |
| `frontend/` | Next.js 16 App Router 키오스크 UI (3000) |
| `.github/` | CI + PR / 이슈 템플릿 |

메뉴 이미지는 `/uploads/**`로 서빙되는데 실제 파일은 두 군데에 있다 — 커밋된 시드 이미지는 `Backend/App/src/main/resources/static/uploads/`(클래스패스), 런타임에 업로드된 파일은 `Backend/App/uploads/`(gitignore). `WebConfig`가 두 위치를 함께 서빙한다.

각 디렉토리에 스택별 `CLAUDE.md`가 따로 있다. 백엔드/프론트 작업 시 그쪽을 참고할 것.

## ⚠️ Gradle 루트는 레포 루트가 아니다

`gradlew`는 `Backend/App/`에 있다. 레포 루트에서 `./gradlew`를 실행하면 실패한다.
모든 gradle 명령은 `cd Backend/App` 후에 실행한다. CI도 `working-directory: Backend/App`로 고정되어 있다.

## 로컬 실행

순서를 지켜야 한다. `docker-compose.yml`이 `.env`의 `DB_USERNAME`/`DB_PASSWORD`를 참조하므로 `.env`가 먼저 있어야 한다.

```bash
# 1) 환경변수 — .env.example 복사 후 DB_PASSWORD 채우기
cd Backend/App && cp .env.example .env

# 2) 인프라 (PostgreSQL 16, Redis 7 — Redis는 아직 미사용)
docker compose up -d

# 3) 백엔드 (8080)
./gradlew bootRun

# 4) 프론트엔드 (3000) — 백엔드가 8080에 떠 있어야 메뉴/주문이 동작한다
cd ../../frontend && npm run dev
```

- API 문서: http://localhost:8080/swagger-ui.html

## 컨벤션

**커밋** — `type: 한국어 설명`. type은 `feat` `fix` `refactor` `test` `chore` `ci` `docs`. scope는 대개 생략한다.

```
feat: 주문 상태 변경 API 추가 (PATCH /api/order/{orderId}/status)
test: OrderControllerTest AbstractIntegrationTest 상속으로 전환
```

**브랜치** — `feature/` `refactor/` `fix/` 접두. **main에 직접 커밋하지 않는다.** PR로만 머지.

**PR** — `.github/PULL_REQUEST_TEMPLATE.md`를 따른다. 특히:
- 새 의존성을 추가하면 `build.gradle.kts`에 **사유 주석을 남긴다**
- 기능을 바꾸면 단위/통합 테스트를 추가한다
- API 스펙이 바뀌면 Swagger에서 확인한다
- FE를 바꾸면 `npm run dev`로 직접 확인하고 스크린샷을 첨부한다

**언어** — 주석, 커밋 메시지, `@DisplayName` 모두 한국어. 이 레포는 "왜 이렇게 했는가"를 길게 설명하는 문화다(학습 프로젝트). 새 코드에서도 의도가 드러나지 않는 결정에는 이유를 남긴다.

## CI

`.github/workflows/ci.yml` — main/develop 대상 PR과 push에서 `./gradlew test --no-daemon`만 돌린다. **프론트엔드는 CI에서 검증되지 않으므로** FE 변경은 로컬에서 직접 확인해야 한다.
