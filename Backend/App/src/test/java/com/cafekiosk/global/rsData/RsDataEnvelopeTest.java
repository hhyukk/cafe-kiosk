package com.cafekiosk.global.rsData;

import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 봉투 회귀 방지선. 이 레포에서 응답을 만드는 메서드는 전부 RsData 를 돌려준다.
 *
 * ── 반환형을 제네릭까지 본다 ──────────────────────────────────────────────────
 *
 * 원시 타입만 보면 ResponseEntity<String> 도 ResponseEntity 라 통과한다. 봉투를 통일하기 전의 위반 여섯 가운데
 * 다섯이 그런 모양이었다. 그래서 반환형을 ResolvableType 으로 풀어, 원시 타입이 RsData 이거나 ResponseEntity 이면서
 * 첫 타입 인자가 RsData 일 때만 통과시킨다. ResponseEntity<?> 와 원시 ResponseEntity 와 Object 는 실패다.
 *
 * ── 오류 응답도 본다 ─────────────────────────────────────────────────────────
 *
 * 핸들러 매핑에는 컨트롤러 메서드만 있다. 예외를 응답으로 바꾸는 @ExceptionHandler 도 봉투의 절반이므로
 * ControllerAdvice 에 있는 것과 컨트롤러 안에 선언된 것까지 함께 훑는다.
 *
 * ── 보지 못하는 것 ──────────────────────────────────────────────────────────
 *
 * 핸들러 메서드를 거치지 않고 /error 로 나가는 응답은 범위 밖이다. 다루지 않은 예외의 500 과 multipart 한도를
 * 넘은 업로드의 413 이 그렇다. 보안 필터가 내는 401 은 SecurityConfigTest 가 봉투까지 본다.
 *
 * 클래스에 설정을 바꾸는 어노테이션을 붙이지 않는다. 붙이면 컨텍스트와 커넥션 풀이 하나 더 뜬다.
 * 지금 모양이면 어노테이션 없이 베이스만 상속한 다른 테스트들과 컨텍스트를 같이 쓴다.
 */
public class RsDataEnvelopeTest extends AbstractIntegrationTest {

    // MVC 가 컨트롤러 요청을 나누는 데 쓰는 바로 그 핸들러 매핑을 이름으로 집는다
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("컨트롤러 메서드와 예외 핸들러는 RsData 나 ResponseEntity<RsData> 만 돌려준다")
    void should_응답을_만드는_메서드는_모두_RsData_봉투를_돌려준다() {
        List<String> 위반 = new ArrayList<>();

        // 핸들러 매핑에는 Boot 의 오류 컨트롤러 같은 라이브러리 것도 들어 있다. 이 레포의 것만 본다
        Set<Class<?>> 컨트롤러 = new LinkedHashSet<>();
        for (HandlerMethod handlerMethod : handlerMapping.getHandlerMethods().values()) {
            Class<?> type = handlerMethod.getBeanType();
            if (우리_코드인가(type)) {
                컨트롤러.add(type);
                검사한다(type, handlerMethod.getMethod(), 위반);
            }
        }

        Set<Class<?>> 어드바이스 = new LinkedHashSet<>();
        for (ControllerAdviceBean adviceBean : ControllerAdviceBean.findAnnotatedBeans(applicationContext)) {
            Class<?> type = adviceBean.getBeanType();
            if (type != null && 우리_코드인가(type)) {
                어드바이스.add(type);
            }
        }

        // 컨트롤러 안의 @ExceptionHandler 는 이 레포의 규칙상 없어야 하지만, 생긴다면 봉투 밖 응답이 숨기 좋은
        // 자리라 같이 본다. 찾는 방식은 Spring 이 예외 핸들러를 찾을 때와 같고 상속받은 메서드까지 본다.
        // selectMethods 는 MetadataLookup 을 받는 오버로드가 있어 람다만 넘기면 모호하므로 MethodFilter 로 못박는다
        Set<Class<?>> 예외핸들러를_찾을_타입 = new LinkedHashSet<>(어드바이스);
        예외핸들러를_찾을_타입.addAll(컨트롤러);
        int 예외핸들러수 = 0;
        for (Class<?> type : 예외핸들러를_찾을_타입) {
            Set<Method> 예외핸들러 = MethodIntrospector.selectMethods(type, (ReflectionUtils.MethodFilter) method ->
                    AnnotatedElementUtils.hasAnnotation(method, ExceptionHandler.class));
            for (Method method : 예외핸들러) {
                검사한다(type, method, 위반);
            }
            예외핸들러수 += 예외핸들러.size();
        }

        // 검사 대상이 비면 마지막 단언이 공허하게 통과한다. 패키지 이름이 바뀌거나 빈을 못 찾는 순간을 여기서 막는다
        assertThat(컨트롤러).as("검사한 컨트롤러").isNotEmpty();
        assertThat(어드바이스).as("검사한 ControllerAdvice").isNotEmpty();
        assertThat(예외핸들러수).as("검사한 예외 핸들러 수").isPositive();

        assertThat(위반).as("RsData 봉투로 감싸지 않은 응답. 이 목록이 곧 고칠 목록이다").isEmpty();
    }

    private static boolean 우리_코드인가(Class<?> type) {
        return type.getName().startsWith("com.cafekiosk.");
    }

    private static void 검사한다(Class<?> type, Method method, List<String> 위반) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        if (!봉투인가(returnType)) {
            위반.add(type.getSimpleName() + "#" + method.getName() + " -> " + returnType);
        }
    }

    // ResponseEntity<?> 의 인자는 Object 경계라 resolve 가 null 을 돌려준다. ResolvableType 이 Object 경계는
    // 풀지 않기 때문이다. 원시 ResponseEntity 는 인자가 없어 getGeneric 이 NONE 이 되고 역시 null 로 풀린다.
    // 둘 다 RsData 와 같지 않으므로 실패한다
    private static boolean 봉투인가(ResolvableType returnType) {
        Class<?> raw = returnType.resolve();
        if (raw == RsData.class) {
            return true;
        }
        return raw == ResponseEntity.class && returnType.getGeneric(0).resolve() == RsData.class;
    }
}
