import { NextRequest, NextResponse } from "next/server";

// Next.js 16부터 라우트 핸들러의 params 는 Promise 다.
export async function DELETE(
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

    const backendResponse = await fetch(
      `http://localhost:8080/api/menu/delete/${menuId}`,
      {
        method: "DELETE",
      }
    );

    if (!backendResponse.ok) {
      let message = "백엔드 메뉴 삭제에 실패했습니다.";
      try {
        const errorText = await backendResponse.text();
        if (errorText) {
          message = errorText;
        }
      } catch {
        // ignore
      }
      return NextResponse.json(
        { message },
        { status: backendResponse.status }
      );
    }

    // 백엔드는 RsData 봉투로 준다. 화면은 message 만 읽으므로 그것만 넘긴다
    let message = "메뉴가 삭제되었습니다.";
    try {
      const responseBody = await backendResponse.json();
      message = responseBody.message || message;
    } catch {
      // ignore
    }

    return NextResponse.json({ message }, { status: 200 });
  } catch (error) {
    console.error("메뉴 삭제 프록시 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}

export async function PUT(
  request: NextRequest,
  context: { params: Promise<{ menuId: string }> }
) {
  try {
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

    const body = await request.json();

    if (!body.category || !body.menu_name || typeof body.price !== "number") {
      return NextResponse.json(
        { message: "필수 필드가 누락되었습니다." },
        { status: 400 }
      );
    }

    // 최종 판정은 백엔드의 Menu 가 한다. 등록처럼 여기서도 범위를 먼저 봐서 서버까지 가지 않고 알려 준다
    if (body.price < 0 || body.price > 10_000_000) {
      return NextResponse.json(
        { message: "메뉴 가격은 0원 이상 10,000,000원 이하만 가능합니다." },
        { status: 400 }
      );
    }

    const backendResponse = await fetch(
      `http://localhost:8080/api/menu/modify/${menuId}`,
      {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          menu_id: Number(menuId),
          menu_name: body.menu_name,
          price: body.price,
          img_url: body.image ?? "",
          category: body.category,
        }),
      }
    );

    if (!backendResponse.ok) {
      let message = "백엔드 메뉴 수정에 실패했습니다.";
      try {
        const errorText = await backendResponse.text();
        if (errorText) {
          message = errorText;
        }
      } catch {
        // ignore
      }
      return NextResponse.json(
        { message },
        { status: backendResponse.status }
      );
    }

    let message = "메뉴가 수정되었습니다.";
    try {
      const data = await backendResponse.json();
      if (data && (data.message || data.msg)) {
        message = data.message || data.msg;
      }
    } catch {
      // ignore (RsData가 text로 올 수도 있음)
    }

    return NextResponse.json({ message }, { status: 200 });
  } catch (error) {
    console.error("메뉴 수정 프록시 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}
