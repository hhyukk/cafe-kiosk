 "use client";

 import React, { useEffect, useMemo, useState } from "react";

 type Product = {
   id: string;
   name: string;
   category?: string;
   price: number;
   img_url?: string;
 };

 type MenuItem = {
   menu_id: number;
   category: string;
   menu_name: string;
   price: number;
   img_url: string;
 };

type OrderHistory = {
  email: string;
  orders: Array<{
    orderNumber: string;
    totalPrice: number;
    items: Array<{
      menuName: string;
      // 주문 시점 가격 스냅샷. 현재 메뉴 가격이 아니다.
      orderPrice: number;
      count: number;
    }>;
  }>;
};

type CartMap = Record<string, number>;

const currency = new Intl.NumberFormat("ko-KR");
const formatPrice = (value: number) => `${currency.format(value)}원`;

export default function Home() {
  const [products, setProducts] = useState<Product[]>([]);
  const [cart, setCart] = useState<CartMap>({});
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCheckoutLoading, setIsCheckoutLoading] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [formData, setFormData] = useState({
    email: "",
    category: "",
    menu_name: "",
    price: "",
    image: "",
  });
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editForm, setEditForm] = useState({
    id: "",
    email: "",
    category: "",
    menu_name: "",
    price: "",
    image: "",
  });
  const [editImageFile, setEditImageFile] = useState<File | null>(null);
  const [editImagePreview, setEditImagePreview] = useState<string>("");

  const [orderForm, setOrderForm] = useState({
    email: "",
  });
  const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);
  const [historyEmail, setHistoryEmail] = useState("");
  const [orderHistory, setOrderHistory] = useState<OrderHistory | null>(null);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);

  const [uploadingImage, setUploadingImage] = useState(false);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string>("");

  const loadProducts = async () => {
    try {
      const res = await fetch("/api/menu", {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
        },
        cache: "no-store",
      });

      if (!res.ok) {
        throw new Error(`서버 응답 오류: ${res.status}`);
      }

      const apiData: MenuItem[] = await res.json();
      console.log("백엔드에서 받은 데이터:", apiData);

      const convertedProducts: Product[] = apiData.map((item) => ({
        id: item.menu_id.toString(),
        name: item.menu_name,
        category: item.category,
        price: item.price,
        img_url: item.img_url,
      }));

      setProducts(convertedProducts);
    } catch (err) {
      console.error("메뉴 데이터 로드 실패:", err);
      alert("메뉴 데이터를 불러오는데 실패했습니다. 백엔드 서버가 실행 중인지 확인해주세요.");
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProducts();
  }, []);

  const addToCart = (id: string) => {
    setCart((prev) => {
      const currentTotal = Object.values(prev).reduce(
          (acc, curr) => acc + curr,
          0,
      );

      if (currentTotal >= 100) {
        alert("주문 수량은 최대 100개까지 가능합니다.");
        return prev;
      }

      return { ...prev, [id]: (prev[id] ?? 0) + 1 };
    });
  };

  const removeFromCart = (id: string) => {
    setCart((prev) => {
      const current = prev[id] ?? 0;
      if (current <= 1) {
        // 1개 이하일 때는 장바구니에서 제거
        const next = { ...prev };
        delete next[id];
        return next;
      }
      // 그 외에는 수량만 1 감소
      return { ...prev, [id]: current - 1 };
    });
  };

  const totalCount = useMemo(
      () => Object.values(cart).reduce((acc, curr) => acc + curr, 0),
      [cart],
  );

  const totalPrice = useMemo(() => {
    return products.reduce((acc, product) => {
      const qty = cart[product.id] ?? 0;
      return acc + product.price * qty;
    }, 0);
  }, [products, cart]);

  const selectedItems = products.filter((product) => cart[product.id]);

  const isValidEmail = (email: string) => {
    // 매우 단순한 이메일 형식 검사
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  };

  const handleCheckout = async () => {
    if (selectedItems.length === 0) return;

    if (!orderForm.email || !isValidEmail(orderForm.email)) {
      alert("유효한 이메일 주소를 입력해주세요.");
      return;
    }

    setIsCheckoutLoading(true);
    try {
      const response = await fetch("/api/order", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: orderForm.email,
          items: selectedItems.map((item) => ({
            menuId: Number(item.id),
            count: cart[item.id],
          })),
        }),
      });

      if (response.ok) {
        // 손님은 대기번호를 받아간다. 전용 주문 완료 화면은 Phase 1에서 만든다.
        const data = await response.json();
        alert(`주문이 완료되었습니다.\n대기번호 ${data.orderNumber}번`);
        setCart({});
        setOrderForm({ email: "" });
      } else {
        const error = await response.json();
        alert(error.message || "주문 처리 중 오류가 발생했습니다.");
      }
    } catch (err) {
      console.error(err);
      alert("주문 처리 중 오류가 발생했습니다.");
    } finally {
      setIsCheckoutLoading(false);
    }
  };

  const handleOpenModal = () => {
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setFormData({
      email: "",
      category: "",
      menu_name: "",
      price: "",
      image: "",
    });
    setImageFile(null);
    setImagePreview("");
  };

  const handleFormChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (!file.type.startsWith('image/')) {
        alert('이미지 파일만 업로드 가능합니다');
        return;
      }

      if (file.size > 5 * 1024 * 1024) {
        alert('파일 크기는 5MB를 초과할 수 없습니다');
        return;
      }

      setImageFile(file);

      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleEditImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (!file.type.startsWith('image/')) {
        alert('이미지 파일만 업로드 가능합니다');
        return;
      }

      if (file.size > 5 * 1024 * 1024) {
        alert('파일 크기는 5MB를 초과할 수 없습니다');
        return;
      }

      setEditImageFile(file);

      const reader = new FileReader();
      reader.onloadend = () => {
        setEditImagePreview(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const uploadImage = async (): Promise<string> => {
    if (!imageFile) return formData.image;

    setUploadingImage(true);
    try {
      const formDataToUpload = new FormData();
      formDataToUpload.append('file', imageFile);

      const response = await fetch('http://localhost:8080/api/upload/image', {
        method: 'POST',
        body: formDataToUpload,
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.message || '이미지 업로드 실패');
      }

      const data = await response.json();
      return data.imageUrl;
    } catch (err) {
      console.error('이미지 업로드 오류:', err);
      throw err;
    } finally {
      setUploadingImage(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      let imageUrl = formData.image;
      if (imageFile) {
        imageUrl = await uploadImage();
      }

      // 2. 메뉴 데이터 전송
      const response = await fetch("/api/menu", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: formData.email,
          category: formData.category,
          menu_name: formData.menu_name,
          price: parseInt(formData.price, 10),
          image: imageUrl, // 업로드된 이미지 URL 또는 입력한 URL
        }),
      });

      if (!response.ok) {
        throw new Error("상품 추가 제안에 실패했습니다");
      }

      const data = await response.json();
      alert(data.message || "상품 추가 제안이 접수되었습니다.");

      // 상품 목록을 즉시 최신 상태로 갱신
      setLoading(true);
      await loadProducts();
      handleCloseModal();
    } catch (err) {
      console.error(err);
      alert("상품 추가 제안 중 오류가 발생했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteMenu = async (product: Product) => {
    if (isDeleting) return;

    const email = window.prompt("삭제 권한 확인을 위해 이메일을 입력해주세요.");
    if (!email) return;

    if (!isValidEmail(email)) {
      alert("유효한 이메일 주소를 입력해주세요.");
      return;
    }

    setIsDeleting(true);
    try {
      const response = await fetch(`/api/menu/${product.id}`, {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email }),
      });

      if (response.ok) {
        const data = await response.json().catch(() => ({}));
        alert(data.message || "메뉴가 삭제되었습니다.");
        setProducts((prev) => prev.filter((p) => p.id !== product.id));
      } else {
        const error = await response.json().catch(() => ({}));
        alert(error.message || "메뉴 삭제에 실패했습니다.");
      }
    } catch (err) {
      console.error(err);
      alert("메뉴 삭제 중 오류가 발생했습니다.");
    } finally {
      setIsDeleting(false);
    }
  };

  const handleOpenHistoryModal = () => {
    setIsHistoryModalOpen(true);
    setOrderHistory(null);
  };

  const handleCloseHistoryModal = () => {
    setIsHistoryModalOpen(false);
    setHistoryEmail("");
    setOrderHistory(null);
    setIsHistoryLoading(false);
  };

  const handleFetchOrderHistory = async () => {
    if (!historyEmail || !isValidEmail(historyEmail)) {
      alert("유효한 이메일 주소를 입력해주세요.");
      return;
    }

    setIsHistoryLoading(true);
    setOrderHistory(null);
    try {
      const response = await fetch("/api/order/history", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email: historyEmail }),
      });

      if (response.ok) {
        const data: OrderHistory = await response.json();
        setOrderHistory(data);
      } else if (response.status === 404) {
        const error = await response.json().catch(() => ({}));
        alert(error.message || "해당 이메일의 주문 내역이 없습니다.");
      } else {
        const error = await response.json().catch(() => ({}));
        alert(error.message || "주문 내역 조회에 실패했습니다.");
      }
    } catch (err) {
      console.error(err);
      alert("주문 내역 조회 중 오류가 발생했습니다.");
    } finally {
      setIsHistoryLoading(false);
    }
  };

  return (
      <div className="flex min-h-screen items-start justify-center bg-slate-100 py-12">
        <main className="flex w-[1200px] max-w-[95vw] gap-6">
          <section className="flex-1 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-semibold text-slate-800">상품 목록</h2>
              </div>
              <button
                  type="button"
                  onClick={handleOpenModal}
                  className="rounded-md border border-emerald-600 px-3 py-1 text-xs font-semibold text-emerald-700 transition hover:bg-emerald-600 hover:text-white whitespace-nowrap"
              >
                상품 추가 제안
              </button>
            </div>

            <div className="overflow-hidden rounded-lg border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="w-[120px] px-4 py-3 text-center">사진</th>
                  <th className="px-4 py-3">상품명</th>
                  <th className="w-28 px-4 py-3 text-right">가격</th>
                  <th className="w-40 px-4 py-3 text-center">Action</th>
                </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                {loading ? (
                    <tr>
                      <td className="px-4 py-6 text-center" colSpan={4}>
                        상품을 불러오는 중입니다...
                      </td>
                    </tr>
                ) : (
                    products.map((product) => (
                        <tr key={product.id} className="hover:bg-slate-50/80">
                          <td className="px-4 py-3">
                            <div className="relative mx-auto flex h-16 w-16 items-center justify-center">
                              {product.img_url ? (
                                  <div className="h-14 w-14 overflow-hidden rounded-md border border-slate-200 bg-slate-100 shadow-inner">
                                    <img
                                        src={product.img_url}
                                        alt={product.name}
                                        className="h-full w-full object-cover"
                                        onError={(e) => {
                                          const target = e.target as HTMLImageElement;
                                          target.style.display = "none";
                                        }}
                                    />
                                  </div>
                              ) : (
                                  <div className="h-14 w-14 rounded-md border border-slate-200 bg-gradient-to-br from-slate-100 to-slate-200 shadow-inner" />
                              )}
                              <button
                                  type="button"
                                  className="absolute -left-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full border border-slate-300 bg-white text-slate-400 hover:border-rose-400 hover:text-rose-500"
                                  title="메뉴 삭제 요청"
                                  onClick={() => handleDeleteMenu(product)}
                                  disabled={isDeleting}
                              >
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    viewBox="0 0 24 24"
                                    className="h-3 w-3"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="2"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                >
                                  <polyline points="3 6 5 6 21 6" />
                                  <path d="M19 6l-1 14H6L5 6" />
                                  <path d="M10 11v6" />
                                  <path d="M14 11v6" />
                                  <path d="M9 6V4h6v2" />
                                </svg>
                              </button>
                            </div>
                          </td>
                          <td className="px-4 py-3 align-top">
                            <div className="text-sm font-semibold text-slate-800">
                              {product.name}
                            </div>
                            {product.category && (
                                <div className="text-xs text-slate-500">
                                  {product.category}
                                </div>
                            )}
                          </td>
                          <td className="px-4 py-3 text-right font-semibold text-slate-800">
                            {formatPrice(product.price)}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-center gap-2 whitespace-nowrap">
                              <button
                                  className="rounded-md border border-emerald-600 px-3 py-1 text-xs font-semibold text-emerald-700 transition hover:bg-emerald-600 hover:text-white whitespace-nowrap"
                                  onClick={() => addToCart(product.id)}
                              >
                                추가
                              </button>
                              <button
                                  className="rounded-md border border-rose-500 px-3 py-1 text-xs font-semibold text-rose-600 transition hover:bg-rose-500 hover:text-white whitespace-nowrap"
                                  onClick={() => removeFromCart(product.id)}
                              >
                                삭제
                              </button>
                              <button
                                  type="button"
                                  className="rounded-md border border-slate-300 px-3 py-1 text-xs font-semibold text-slate-600 transition hover:bg-slate-100 whitespace-nowrap"
                                  onClick={() => {
                                    setIsEditModalOpen(true);
                                    setEditForm({
                                      id: product.id,
                                      email: "",
                                      category: product.category ?? "",
                                      menu_name: product.name,
                                      price: String(product.price),
                                      image: product.img_url ?? "",
                                    });
                                  }}
                              >
                                수정
                              </button>
                            </div>
                          </td>
                        </tr>
                    ))
                )}
                {!loading && products.length === 0 && (
                    <tr>
                      <td className="px-4 py-6 text-center text-slate-500" colSpan={4}>
                        표시할 상품이 없습니다.
                      </td>
                    </tr>
                )}
                </tbody>
              </table>
            </div>
          </section>

          <section className="w-[360px] rounded-lg border border-slate-200 bg-slate-100/70 p-4 shadow-sm">
            <h2 className="mb-3 text-base font-semibold text-slate-800">Summary</h2>
            <div className="mb-3 space-y-2 rounded-md bg-white p-3 text-sm shadow-inner">
              {selectedItems.length === 0 ? (
                  <p className="text-xs text-slate-500">선택된 상품이 없습니다.</p>
              ) : (
                  selectedItems.map((item) => (
                      <div key={item.id} className="flex items-center justify-between text-slate-700">
                        <div className="flex items-center gap-2">
                          <span className="h-2 w-2 rounded-full bg-slate-400" />
                          <span>{item.name}</span>
                        </div>
                        <span className="text-xs text-slate-500">x{cart[item.id]}</span>
                      </div>
                  ))
              )}
            </div>

            <div className="space-y-2 text-sm">
              <label className="block space-y-1">
                <span className="text-slate-600">이메일</span>
                <input
                    type="email"
                    value={orderForm.email}
                    onChange={(e) =>
                        setOrderForm((prev) => ({ ...prev, email: e.target.value }))
                    }
                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                    placeholder="your@email.com"
                />
              </label>
            </div>

            <p className="mt-4 text-xs text-slate-500">
              결제 후 대기번호를 받아 카운터에서 음료를 받아가세요.
            </p>

            <div className="mt-4 flex items-center justify-between rounded-md bg-white px-3 py-2 text-sm shadow-inner">
              <span className="text-slate-600">총 금액</span>
              <div className="text-right">
                <div className="text-lg font-bold text-slate-900">
                  {formatPrice(totalPrice)}
                </div>
                <div className="text-xs text-slate-500">
                  {totalCount}개 선택됨
                </div>
              </div>
            </div>

            <button
                onClick={handleCheckout}
                className="mt-4 w-full rounded-md bg-slate-900 py-3 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
                disabled={selectedItems.length === 0 || isCheckoutLoading}
            >
              {isCheckoutLoading ? "처리 중..." : "결제하기"}
            </button>

            <button
                onClick={handleOpenHistoryModal}
                className="mt-2 w-full rounded-md border border-slate-300 bg-white py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
            >
              주문 내역 조회
            </button>

          </section>
        </main>

        {/* 상품 추가 제안 모달 */}
        {isModalOpen && (
            <div
                className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
                onClick={handleCloseModal}
            >
              <div
                  className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl"
                  onClick={(e) => e.stopPropagation()}
              >
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-xl font-semibold text-slate-800">
                    상품 추가 제안
                  </h3>
                  <button
                      onClick={handleCloseModal}
                      className="text-slate-400 transition hover:text-slate-600"
                      disabled={isSubmitting}
                  >
                    <svg
                        className="h-6 w-6"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                      <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  </button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                  <label className="block space-y-1">
                <span className="text-sm font-medium text-slate-700">
                  이메일
                </span>
                    <input
                        type="email"
                        name="email"
                        value={formData.email}
                        onChange={handleFormChange}
                        required
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="your@email.com"
                    />
                  </label>

                  <label className="block space-y-1">
                    <span className="text-sm font-medium text-slate-700">분류</span>
                    <input
                        type="text"
                        name="category"
                        value={formData.category}
                        onChange={handleFormChange}
                        required
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="분류를 입력하세요 (예: 커피콩, 식품, 의류)"
                    />
                  </label>

                  <label className="block space-y-1">
                <span className="text-sm font-medium text-slate-700">
                  제품명
                </span>
                    <input
                        type="text"
                        name="menu_name"
                        value={formData.menu_name}
                        onChange={handleFormChange}
                        required
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="제품명을 입력하세요"
                    />
                  </label>

                  <label className="block space-y-1">
                    <span className="text-sm font-medium text-slate-700">가격</span>
                    <input
                        type="number"
                        name="price"
                        value={formData.price}
                        onChange={handleFormChange}
                        required
                        min="0"
                        max="10000000"
                        step="1"
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="0"
                    />
                  </label>

                  <div className="block space-y-2">
                    <span className="text-sm font-medium text-slate-700">이미지</span>

                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <input
                          type="file"
                          accept="image/*"
                          onChange={handleImageChange}
                          className="flex-1 text-sm text-slate-600 file:mr-4 file:rounded-md file:border-0 file:bg-emerald-50 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-emerald-700 hover:file:bg-emerald-100"
                        />

                        {imageFile && (
                          <button
                            type="button"
                            onClick={() => {
                              setImageFile(null);
                              setImagePreview("");
                            }}
                            className="rounded-md border border-slate-300 px-3 py-1 text-xs font-semibold text-slate-600 transition hover:bg-slate-100"
                          >
                            취소
                          </button>
                        )}
                      </div>

                      {imagePreview && (
                        <div className="mt-2">
                          <img
                            src={imagePreview}
                            alt="미리보기"
                            className="h-32 w-32 rounded-md border border-slate-200 object-cover"
                          />
                        </div>
                      )}

                      {uploadingImage && (
                        <p className="text-xs text-emerald-600">이미지 업로드 중...</p>
                      )}
                    </div>
                  </div>

                  <div className="flex gap-2 pt-2">
                    <button
                        type="button"
                        onClick={handleCloseModal}
                        disabled={isSubmitting}
                        className="flex-1 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      취소
                    </button>
                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="flex-1 rounded-md bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isSubmitting ? "제출 중..." : "제안하기"}
                    </button>
                  </div>
                </form>
              </div>
            </div>
        )}

        {/* 주문 내역 조회 모달 */}
        {isHistoryModalOpen && (
            <div
                className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
                onClick={handleCloseHistoryModal}
            >
              <div
                  className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl"
                  onClick={(e) => e.stopPropagation()}
              >
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-xl font-semibold text-slate-800">
                    주문 내역 조회
                  </h3>
                  <button
                      onClick={handleCloseHistoryModal}
                      className="text-slate-400 transition hover:text-slate-600"
                      disabled={isHistoryLoading}
                  >
                    <svg
                        className="h-6 w-6"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                      <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  </button>
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="block space-y-1">
                  <span className="text-sm font-medium text-slate-700">
                    이메일
                  </span>
                      <input
                          type="email"
                          value={historyEmail}
                          onChange={(e) => setHistoryEmail(e.target.value)}
                          className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                          placeholder="주문 시 사용한 이메일을 입력하세요"
                          disabled={isHistoryLoading}
                      />
                    </label>
                    <button
                        type="button"
                        onClick={handleFetchOrderHistory}
                        disabled={isHistoryLoading}
                        className="mt-3 w-full rounded-md bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isHistoryLoading ? "조회 중..." : "주문 내역 조회"}
                    </button>
                  </div>

                  {orderHistory && (
                      <div className="mt-4 space-y-3 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
                        <div className="mb-2 flex items-center justify-between">
                          <div>
                            <div className="font-semibold text-slate-800">
                              {orderHistory.email}
                            </div>
                            <div className="text-xs text-slate-500">
                              총 {orderHistory.orders.length}건의 주문
                            </div>
                          </div>
                        </div>

                        <div className="max-h-64 space-y-3 overflow-y-auto">
                          {orderHistory.orders.map((order, orderIdx) => (
                              <div
                                  key={orderIdx}
                                  className="rounded-md border border-slate-200 bg-white"
                              >
                                <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2 text-xs text-slate-600">
                          <span className="font-semibold text-slate-800">
                            대기번호 {order.orderNumber}
                          </span>
                                  <span>{formatPrice(order.totalPrice)}</span>
                                </div>
                                <table className="w-full text-left text-xs">
                                  <thead className="bg-slate-100 text-slate-600">
                                  <tr>
                                    <th className="px-3 py-2">메뉴명</th>
                                    <th className="w-20 px-3 py-2 text-right">
                                      가격
                                    </th>
                                    <th className="w-16 px-3 py-2 text-center">
                                      수량
                                    </th>
                                  </tr>
                                  </thead>
                                  <tbody className="divide-y divide-slate-100">
                                  {(order.items ?? []).map((item, idx) => (
                                      <tr key={idx}>
                                        <td className="px-3 py-2">{item.menuName}</td>
                                        <td className="px-3 py-2 text-right">
                                          {formatPrice(item.orderPrice)}
                                        </td>
                                        <td className="px-3 py-2 text-center">
                                          x{item.count}
                                        </td>
                                      </tr>
                                  ))}
                                  </tbody>
                                </table>
                              </div>
                          ))}
                        </div>
                      </div>
                  )}
                </div>
              </div>
            </div>
        )}

        {/* 메뉴 수정 모달 */}
        {isEditModalOpen && (
            <div
                className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
                onClick={() => {
                  setIsEditModalOpen(false);
                  setEditForm({
                    id: "",
                    email: "",
                    category: "",
                    menu_name: "",
                    price: "",
                    image: "",
                  });
                }}
            >
              <div
                  className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl"
                  onClick={(e) => e.stopPropagation()}
              >
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-xl font-semibold text-slate-800">
                    메뉴 수정
                  </h3>
                  <button
                      onClick={() => {
                        setIsEditModalOpen(false);
                        setEditForm({
                          id: "",
                          email: "",
                          category: "",
                          menu_name: "",
                          price: "",
                          image: "",
                        });
                      }}
                      className="text-slate-400 transition hover:text-slate-600"
                  >
                    <svg
                        className="h-6 w-6"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                      <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  </button>
                </div>

                <form
                    onSubmit={async (e: React.FormEvent) => {
                      e.preventDefault();
                      if (!editForm.id) return;

                      if (!editForm.email || !isValidEmail(editForm.email)) {
                        alert("유효한 이메일 주소를 입력해주세요.");
                        return;
                      }

                      const numericPrice = parseInt(editForm.price, 10);
                      if (
                        Number.isNaN(numericPrice) ||
                        numericPrice < 0 ||
                        numericPrice > 10_000_000
                      ) {
                        alert("가격은 0원 이상 10,000,000원 이하만 가능합니다.");
                        return;
                      }

                      try {
                        let imageUrl = editForm.image;
                        if (editImageFile) {
                          setUploadingImage(true);
                          try {
                            const formDataToUpload = new FormData();
                            formDataToUpload.append('file', editImageFile);

                            const uploadResponse = await fetch('http://localhost:8080/api/upload/image', {
                              method: 'POST',
                              body: formDataToUpload,
                            });

                            if (!uploadResponse.ok) {
                              const error = await uploadResponse.json();
                              throw new Error(error.message || '이미지 업로드 실패');
                            }

                            const uploadData = await uploadResponse.json();
                            imageUrl = uploadData.imageUrl;
                          } finally {
                            setUploadingImage(false);
                          }
                        }

                        // 메뉴 수정 요청
                        const response = await fetch(`/api/menu/${editForm.id}`, {
                          method: "PUT",
                          headers: {
                            "Content-Type": "application/json",
                          },
                          body: JSON.stringify({
                            email: editForm.email,
                            category: editForm.category,
                            menu_name: editForm.menu_name,
                            price: numericPrice,
                            image: imageUrl || "",  // 업로드된 URL 사용
                          }),
                        });

                        if (!response.ok) {
                          if (response.status === 401) {
                            alert("이메일이 잘못되었습니다.");
                            return;
                          }
                          const error = await response.json().catch(() => ({}));
                          throw new Error(error.message || "메뉴 수정에 실패했습니다.");
                        }

                        const data = await response.json().catch(() => ({}));
                        alert(data.message || "메뉴가 수정되었습니다.");

                        // 프론트 상태 업데이트
                        setProducts((prev) =>
                          prev.map((p) =>
                            p.id === editForm.id
                              ? {
                                  ...p,
                                  name: editForm.menu_name,
                                  category: editForm.category,
                                  price: numericPrice,
                                  img_url: imageUrl || p.img_url,  // 업로드된 URL 사용
                                }
                              : p
                          )
                        );

                        setIsEditModalOpen(false);
                        setEditForm({
                          id: "",
                          email: "",
                          category: "",
                          menu_name: "",
                          price: "",
                          image: "",
                        });
                        setEditImageFile(null);
                        setEditImagePreview("");
                      } catch (err) {
                        console.error(err);
                        alert("메뉴 수정 중 오류가 발생했습니다.");
                      }
                    }}
                    className="space-y-4"
                >
                  <label className="block space-y-1">
                <span className="text-sm font-medium text-slate-700">
                  이메일
                </span>
                    <input
                        type="email"
                        name="email"
                        value={editForm.email}
                        onChange={(e) =>
                            setEditForm((prev) => ({ ...prev, email: e.target.value }))
                        }
                        required
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="등록 시 사용한 이메일을 입력하세요"
                    />
                  </label>

                  <label className="block space-y-1">
                    <span className="text-sm font-medium text-slate-700">분류</span>
                    <input
                        type="text"
                        name="category"
                        value={editForm.category}
                        onChange={(e) =>
                            setEditForm((prev) => ({
                              ...prev,
                              category: e.target.value,
                            }))
                        }
                        required
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="분류를 입력하세요"
                    />
                  </label>

                  <label className="block space-y-1">
                <span className="text-sm font-medium text-slate-700">
                  제품명
                </span>
                    <input
                        type="text"
                        name="menu_name"
                        value={editForm.menu_name}
                        onChange={(e) =>
                            setEditForm((prev) => ({
                              ...prev,
                              menu_name: e.target.value,
                            }))
                        }
                        required
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="제품명을 입력하세요"
                    />
                  </label>

                  <label className="block space-y-1">
                    <span className="text-sm font-medium text-slate-700">가격</span>
                    <input
                        type="number"
                        name="price"
                        value={editForm.price}
                        onChange={(e) => {
                          const value = e.target.value;
                          const numeric = parseInt(value, 10);
                          if (!Number.isNaN(numeric) && numeric > 10000000) {
                            alert("가격은 최대 10,000,000원까지 가능합니다.");
                            setEditForm((prev) => ({ ...prev, price: "10000000" }));
                            return;
                          }
                          setEditForm((prev) => ({ ...prev, price: value }));
                        }}
                        required
                        min="0"
                        max="10000000"
                        step="1"
                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none ring-emerald-500/60 transition focus:ring"
                        placeholder="0"
                    />
                  </label>

                  <div className="block space-y-2">
                    <span className="text-sm font-medium text-slate-700">이미지 (선택사항)</span>

                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <input
                          type="file"
                          accept="image/*"
                          onChange={handleEditImageChange}
                          className="flex-1 text-sm text-slate-600 file:mr-4 file:rounded-md file:border-0 file:bg-emerald-50 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-emerald-700 hover:file:bg-emerald-100"
                        />

                        {editImageFile && (
                          <button
                            type="button"
                            onClick={() => {
                              setEditImageFile(null);
                              setEditImagePreview("");
                            }}
                            className="rounded-md border border-slate-300 px-3 py-1 text-xs font-semibold text-slate-600 transition hover:bg-slate-100"
                          >
                            취소
                          </button>
                        )}
                      </div>

                      {editImagePreview && (
                        <div className="mt-2">
                          <img
                            src={editImagePreview}
                            alt="미리보기"
                            className="h-32 w-32 rounded-md border border-slate-200 object-cover"
                          />
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="flex gap-2 pt-2">
                    <button
                        type="button"
                        onClick={() => {
                          setIsEditModalOpen(false);
                          setEditForm({
                            id: "",
                            email: "",
                            category: "",
                            menu_name: "",
                            price: "",
                            image: "",
                          });
                          setEditImageFile(null);  // 추가
                          setEditImagePreview(""); // 추가
                        }}
                        className="flex-1 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                    >
                      취소
                    </button>
                    <button
                        type="submit"
                        className="flex-1 rounded-md bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-700"
                    >
                      수정하기
                    </button>
                  </div>
                </form>
              </div>
            </div>
        )}
      </div>
  );
}