import { NextRequest, NextResponse } from "next/server";

export async function GET() {
  try {
    const backendResponse = await fetch("http://localhost:8080/api/menu", {
      cache: "no-store",
    });

    if (!backendResponse.ok) {
      let errorMessage = "백엔드 메뉴 조회에 실패했습니다.";
      try {
        const errorBody = await backendResponse.text();
        if (errorBody) errorMessage = errorBody;
      } catch {
        // ignore
      }
      return NextResponse.json(
        { message: errorMessage },
        { status: backendResponse.status }
      );
    }

    const data = await backendResponse.json();
    return NextResponse.json(data, { status: 200 });
  } catch (error) {
    console.error("메뉴 조회 프록시 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // 요청 데이터 검증
    if (
      !body.category ||
      !body.menu_name ||
      typeof body.price !== "number"
    ) {
      return NextResponse.json(
        { message: "필수 필드가 누락되었습니다." },
        { status: 400 }
      );
    }

    // 가격 상한 검증 (품목 제안 최대 10,000,000원)
    if (body.price < 0 || body.price > 10_000_000) {
      return NextResponse.json(
        { message: "품목 제안 금액은 0원 이상 10,000,000원 이하만 가능합니다." },
        { status: 400 }
      );
    }

    // 실제 백엔드 서버로 데이터 전송
    const backendResponse = await fetch("http://localhost:8080/api/menu", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        category: body.category,
        menuName: body.menu_name,
        price: body.price,
        imageURL: body.image ?? "",
      }),
    });

    if (!backendResponse.ok) {
      let errorMessage = "백엔드 메뉴 생성에 실패했습니다.";
      try {
        const errorText = await backendResponse.text();
        errorMessage = errorText || errorMessage;
      } catch {
        // ignore
      }
      return NextResponse.json(
        { message: errorMessage },
        { status: backendResponse.status }
      );
    }

    let message = "생성되었습니다.";
    try {
      const text = await backendResponse.text();
      if (text) {
        message = text;
      }
    } catch {
      // ignore
    }

    // 성공 응답
    return NextResponse.json({ message }, { status: 200 });
  } catch (error) {
    console.error("상품 추가 제안 오류:", error);
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}

