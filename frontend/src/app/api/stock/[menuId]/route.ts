import { NextRequest, NextResponse } from "next/server";

// Next.js 16부터 라우트 핸들러의 params 는 Promise 다.
//
// 이 라우트를 부르는 화면은 아직 없다. 재고 조정은 관리자 기능이고 손님 화면에
// 관리자 기능을 새로 얹지 않기로 했으므로, 실제 사용은 관리자 화면이 생기는 단계에서 시작된다.
export async function PATCH(
  request: NextRequest,
  context: { params: Promise<{ menuId: string }> }
) {
  try {
    // Next context 및 URL 양쪽에서 menuId 추출 (혹시 context 전달이 안 되는 경우 대비)
    let menuId: string | undefined = (await context.params)?.menuId;
    if (!menuId) {
      const segments = request.nextUrl.pathname.split("/").filter(Boolean);
      menuId = segments[segments.length - 1];
    }

    if (!menuId) {
      return NextResponse.json(
        { message: "메뉴 ID가 제공되지 않았습니다." },
        { status: 400 }
      );
    }

    const body = await request.json().catch(() => ({}));

    // 0 은 유효한 수량이다. 품절 처리를 하는 방법이 이것뿐이므로 !body.quantity 같은
    // falsy 검사로 거르면 안 된다. 그렇게 하면 품절 처리가 통째로 400 이 되고,
    // 그 사실이 오류 메시지에 드러나지도 않는다.
    if (
      typeof body.quantity !== "number" ||
      !Number.isInteger(body.quantity) ||
      body.quantity < 0
    ) {
      return NextResponse.json(
        { message: "재고 수량은 0 이상의 정수여야 합니다." },
        { status: 400 }
      );
    }

    const backendResponse = await fetch(
      `http://localhost:8080/api/stock/${menuId}`,
      {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ quantity: body.quantity }),
      }
    );

    if (!backendResponse.ok) {
      let message = "재고 조정에 실패했습니다.";
      try {
        const errorBody = await backendResponse.json();
        message = errorBody.message || errorBody.msg || errorBody.error || message;
      } catch {
        // ignore
      }
      return NextResponse.json(
        { message },
        { status: backendResponse.status }
      );
    }

    // 확정된 수량을 그대로 내보낸다. 다른 라우트가 message 하나로 줄이는 것과 다른 이유는
    // 부르는 쪽이 그 숫자를 알아야 목록을 다시 읽지 않고 화면을 갱신할 수 있어서다.
    let message = "재고를 조정하였습니다.";
    let data = null;
    try {
      const responseBody = await backendResponse.json();
      message = responseBody.message || message;
      data = responseBody.data ?? null;
    } catch {
      // ignore
    }

    return NextResponse.json({ message, data }, { status: 200 });
  } catch (error) {
    console.error("재고 조정 프록시 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}
