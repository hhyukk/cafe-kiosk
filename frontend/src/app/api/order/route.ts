import { NextRequest, NextResponse } from "next/server";

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // 요청 데이터 검증 (백엔드 OrderDto.CreateRequest 기준)
    if (!body.email) {
      return NextResponse.json(
        { message: "고객 정보가 올바르지 않습니다." },
        { status: 400 }
      );
    }

    if (
      !body.items ||
      !Array.isArray(body.items) ||
      body.items.length === 0
    ) {
      return NextResponse.json(
        { message: "주문 상품 정보가 없습니다." },
        { status: 400 }
      );
    }

    // 주문 최대 수량 100개 제한 (전체 수량 기준)
    const totalCount = body.items.reduce(
      (sum: number, item: any) => sum + (item?.count ?? 0),
      0
    );
    if (totalCount <= 0 || totalCount > 100) {
      return NextResponse.json(
        { message: "주문 수량은 1개 이상 100개 이하만 가능합니다." },
        { status: 400 }
      );
    }

    // 실제 백엔드 서버로 주문 데이터 전송
    const backendResponse = await fetch("http://localhost:8080/api/order", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: body.email,
        items: body.items.map((item: any) => ({
          menuId: item.menuId,
          count: item.count,
        })),
      }),
    });

    if (!backendResponse.ok) {
      let errorMessage = "주문 처리에 실패했습니다.";
      try {
        const errorBody = await backendResponse.json();
        errorMessage =
          errorBody.message || errorBody.msg || errorBody.error || errorMessage;
      } catch {
        try {
          const text = await backendResponse.text();
          if (text) errorMessage = text;
        } catch {
          // ignore
        }
      }
      return NextResponse.json(
        { message: errorMessage },
        { status: backendResponse.status }
      );
    }

    let responseJson: any = {};
    try {
      responseJson = await backendResponse.json();
    } catch {
      // 빈 바디일 수 있음
    }

    // 성공 응답 (200 OK)
    return NextResponse.json(responseJson, { status: 200 });
  } catch (error) {
    console.error("결제 처리 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}

