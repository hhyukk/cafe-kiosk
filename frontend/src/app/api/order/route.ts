import { NextRequest, NextResponse } from "next/server";

// 브라우저가 보낸 주문 아이템이다. 아래 검증을 지나기 전에는 모양을 믿을 수 없어 필드를 선택으로 둔다
type OrderItemInput = {
  menuId?: number;
  count?: number;
};

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
      (sum: number, item: OrderItemInput) => sum + (item?.count ?? 0),
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
        items: body.items.map((item: OrderItemInput) => ({
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

    // 백엔드는 RsData 봉투로 준다. 주문 조회 라우트처럼 message 와 data 만 골라 넘긴다
    const responseBody = await backendResponse.json();
    return NextResponse.json(
      { message: responseBody.message, data: responseBody.data },
      { status: 200 }
    );
  } catch (error) {
    console.error("결제 처리 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}

