package com.cafekiosk.auth.service;

import com.cafekiosk.auth.dto.AuthDto;
import com.cafekiosk.auth.entity.Owner;
import com.cafekiosk.auth.exception.InvalidCredentialsException;
import com.cafekiosk.auth.jwt.AccessTokenIssuer;
import com.cafekiosk.auth.repository.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 점주 로그인. 이메일과 비밀번호를 확인하고 액세스 토큰을 내준다.
 *
 * ── 없는 이메일에도 해시 비교를 한 번 치른다 ───────────────────────────────────────
 *
 * BCrypt 비교는 일부러 느리게 만든 연산이다. 없는 이메일이면 비교 없이 곧바로 돌려보내고 있는
 * 이메일이면 비교를 하고 돌려보내면, 응답 메시지를 똑같이 맞춰도 걸린 시간으로 계정이 있는지가
 * 드러난다. 그래서 없는 이메일에도 미리 만들어 둔 해시와 한 번 비교한다. 결과는 버린다.
 *
 * ── DaoAuthenticationProvider 를 쓰지 않는 이유 ────────────────────────────────────
 *
 * Spring Security 의 그 프로바이더가 위와 같은 방어를 이미 갖고 있다. 그래도 여기서 직접 하는
 * 이유는 예외 때문이다. 그 경로에서는 DB 장애도 InternalAuthenticationServiceException 으로 감싸여
 * AuthenticationException 계열로 나온다. 보안 필터의 인증 실패를 401 로 옮기는 핸들러가 그 계열을
 * 받으므로, 장애가 500 이 아니라 로그인이 필요하다는 401 로 둔갑한다. 여기서는 로그인 실패만
 * InvalidCredentialsException 이 되고 DB 장애는 그대로 500 으로 나간다.
 */
@Service
public class AuthService {

    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;

    // 없는 이메일과 비교할 해시. 원문은 아무도 모르는 난수라 이 해시와 맞는 비밀번호는 없다
    private final String dummyHash;

    public AuthService(
            OwnerRepository ownerRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer) {
        this.ownerRepository = ownerRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public AuthDto.LoginResponse login(String email, String rawPassword) {
        Optional<Owner> found = ownerRepository.findByEmail(email);

        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw new InvalidCredentialsException();
        }

        Owner owner = found.get();
        if (!owner.matchesPassword(rawPassword, passwordEncoder)) {
            throw new InvalidCredentialsException();
        }

        String accessToken = accessTokenIssuer.issue(owner.getEmail(), owner.getRole());
        return new AuthDto.LoginResponse(accessToken, owner.getEmail(), owner.getRole());
    }
}
