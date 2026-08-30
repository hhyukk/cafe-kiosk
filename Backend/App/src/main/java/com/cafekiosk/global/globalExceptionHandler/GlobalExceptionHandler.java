package com.cafekiosk.global.globalExceptionHandler;

import com.cafekiosk.global.rsData.RsData;
import com.cafekiosk.order.exception.InvalidOrderStatusTransitionException;
import com.cafekiosk.stock.exception.OutOfStockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
