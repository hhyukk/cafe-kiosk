package com.cafekiosk.global.globalExceptionHandler;

import com.cafekiosk.auth.exception.InvalidCredentialsException;
import com.cafekiosk.global.rsData.RsData;
import com.cafekiosk.order.exception.InvalidOrderStatusTransitionException;
import com.cafekiosk.stock.exception.OutOfStockException;
import com.cafekiosk.stock.exception.StockLockTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // 404 : NOT FOUND
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<RsData<Void>> handleException(NoSuchElementException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "404-1",
                        "해당 데이터가 존재하지 않습니다."
                ),
                NOT_FOUND
        );
    }

    // 400 : BAD REQUEST
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handleException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .filter(error -> error instanceof FieldError)
                .map(error -> (FieldError) error)
                .map(error -> error.getField() + "-" + error.getCode() + "-" + error.getDefaultMessage())
                .sorted(Comparator.comparing(String::toString))
                .collect(Collectors.joining("\n"));

        return new ResponseEntity<>(
                new RsData<>(
                        "400-1",
                        message
                ),
                BAD_REQUEST
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RsData<Void>> handle(IllegalArgumentException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "400-1",
                        ex.getMessage()
                ),
                BAD_REQUEST
        );
    }

    // 401 : UNAUTHORIZED. 점주 API 를 토큰 없이, 또는 위조되거나 만료된 토큰으로 부른 경우.
    //       이 예외는 컨트롤러가 아니라 보안 필터에서 터지므로 DispatcherServlet 을 거치지 않는다.
    //       RsDataAuthenticationEntryPoint 가 리졸버로 넘겨줘야 여기 닿는다. 이유는 그쪽 주석에 있다.
    //
    //       토큰이 없는 것과 만료된 것을 메시지로 가르지 않는다. 어느 쪽이든 점주가 할 일은 다시
    //       로그인하는 것 하나다. 토큰이 깨졌다는 사실은 WWW-Authenticate 헤더의 오류 코드에 남는다.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RsData<Void>> handle(AuthenticationException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "401-1",
                        "로그인이 필요합니다."
                ),
                UNAUTHORIZED
        );
    }

    // 401 : UNAUTHORIZED. 로그인 실패. 이메일이 없는 것과 비밀번호가 틀린 것을 같은 응답으로 묶는다.
    //       어느 쪽인지 알려 주면 계정이 있는지가 응답으로 샌다. 위 핸들러와 예외를 나눈 이유는
    //       InvalidCredentialsException 주석에 있다.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<RsData<Void>> handle(InvalidCredentialsException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "401-1",
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ),
                UNAUTHORIZED
        );
    }

    // 409 : CONFLICT. 현재 주문 상태와 충돌하는 전이 시도
    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<RsData<Void>> handle(InvalidOrderStatusTransitionException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "409-1",
                        ex.getMessage()
                ),
                CONFLICT
        );
    }

    // 409 : CONFLICT. 남은 재고보다 많이 주문한 경우.
    //       부족 판단은 Stock 엔티티가 하고 여기서는 HTTP 로 옮기기만 한다.
    //       메시지에는 메뉴 이름이 아니라 menuId 가 담긴다. 이유는 OutOfStockException 주석에 있다.
    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<RsData<Void>> handle(OutOfStockException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "409-1",
                        ex.getMessage()
                ),
                CONFLICT
        );
    }

    // 409 : CONFLICT. 낙관적 락 재시도를 다 쓰고도 버전 충돌을 못 벗어난 경우.
    //       OrderFacade 가 상한까지 다시 시도한 뒤에야 여기 닿으므로, 이 응답이 나갔다면
    //       손님이 잠깐 다시 누르면 되는 상황이 아니라 그 재고 행에 뭔가 이상이 있는 것이다.
    //
    //       상태 전이 충돌이나 재고 부족과 같은 409-1 계열로 묶는다. 셋 다 요청이 잘못된 것이
    //       아니라 지금 자원 상태와 부딪힌 것이고, 손님이 할 수 있는 일도 다시 시도하는 것 하나다.
    //
    //       예외 메시지를 그대로 쓰지 않는다. Hibernate 가 넣는 문장에 엔티티 클래스명과
    //       식별자가 담겨 있어 손님 화면에 나갈 물건이 아니다. 원문은 스택 트레이스에 남는다.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<RsData<Void>> handle(OptimisticLockingFailureException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "409-1",
                        "주문이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
                ),
                CONFLICT
        );
    }

    // 503 : SERVICE UNAVAILABLE. 분산 락을 대기 한계 안에 얻지 못한 경우.
    //       409 계열과 나누는 이유는 사건의 종류가 다르기 때문이다. 재고 부족과 상태 전이
    //       충돌은 자원의 현재 상태와 부딪힌 것이라 그 상태가 바뀌지 않는 한 다시 눌러도
    //       같은 답이지만, 이쪽은 자원에 닿아 보지도 못했고 줄이 짧아지면 통과한다.
    //
    //       예외 메시지에 menuId 가 담겨 있어 그대로 쓰지 않는다. 손님이 알 것도 아니고
    //       할 수 있는 일도 다시 시도하는 것 하나다. 원문은 스택 트레이스에 남는다.
    @ExceptionHandler(StockLockTimeoutException.class)
    public ResponseEntity<RsData<Void>> handle(StockLockTimeoutException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "503-1",
                        "주문이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
                ),
                SERVICE_UNAVAILABLE
        );
    }

    // 400 : 쿼리 파라미터와 경로 변수의 타입 변환 실패 (예: ?status=NOPE → OrderStatus 변환 불가).
    //       바디 검증용 MethodArgumentNotValidException 과 다른 계열이라 별도 매핑이 필요하다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RsData<Void>> handle(MethodArgumentTypeMismatchException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "400-1",
                        "잘못된 요청 파라미터입니다."
                ),
                BAD_REQUEST
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RsData<Void>> handleException(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>(
                new RsData<>(
                        "400-1",
                        "요청 본문이 올바르지 않습니다."
                ),
                BAD_REQUEST
        );
    }
}
