package com.cafekiosk.global.globalExceptionHandler;

import com.cafekiosk.global.rsData.RsData;
import com.cafekiosk.order.exception.InvalidOrderStatusTransitionException;
import com.cafekiosk.stock.exception.OutOfStockException;
import lombok.RequiredArgsConstructor;
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
