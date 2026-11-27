import { NextRequest, NextResponse } from "next/server";

// 손님이 받은 대기번호로 주문 하나를 조회한다. 브라우저는 번호를 POST 본문으로 보내고 백엔드에는
// GET /api/order/{orderNumber} 로 중계한다. 라우트 이름이 history 인 것은 이메일로 주문 내역을
// 통째 조회하던 때의 이름이다. 손님 주문 화면을 만드는 단계에서 라우트 모양을 다시 정한다.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json().catch(() => ({}));
    const orderNumber = body?.orderNumber;

    // 숫자만 백엔드로 보낸다. 이 값은 백엔드 URL 의 경로 한 칸이 되는데, ../orders 같은 값을 그대로
    // 이어 붙이면 fetch 가 경로를 정규화해 다른 백엔드 경로를 부른다. 지금은 그 경로가 토큰 없이 401 로
    // 막히지만, BFF 가 점주 토큰을 싣기 시작하면 점주 API 로 이어진다. encodeURIComponent 는 점을
    // 이스케이프하지 않으므로 이 검사를 대신하지 못한다.
    if (typeof orderNumber !== "string" || !/^\d+$/.test(orderNumber)) {
      return NextResponse.json(
        { message: "대기번호는 숫자로 입력해주세요." },
        { status: 400 }
      );
    }

    const backendResponse = await fetch(
      `http://localhost:8080/api/order/${orderNumber}`,
      {
        method: "GET",
        // 주문 상태는 계속 바뀐다. 캐시된 응답이 돌아오면 손님이 지난 상태를 보게 된다
        cache: "no-store",
      }
    );

    if (backendResponse.status === 404) {
      return NextResponse.json(
        { message: "해당 대기번호의 주문이 없습니다." },
        { status: 404 }
      );
    }

    if (!backendResponse.ok) {
      let message = "주문 조회에 실패했습니다.";
      try {
        const errorBody = await backendResponse.json();
        message =
          errorBody.message || errorBody.msg || errorBody.error || message;
      } catch {
        // ignore
      }
      return NextResponse.json(
        { message },
        { status: backendResponse.status }
      );
    }

    // 백엔드는 RsData 봉투로 준다. 재고 라우트처럼 message 와 data 만 골라 넘긴다
    const responseBody = await backendResponse.json();
    return NextResponse.json(
      { message: responseBody.message, data: responseBody.data },
      { status: 200 }
    );
  } catch (error) {
    console.error("주문 조회 프록시 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}
