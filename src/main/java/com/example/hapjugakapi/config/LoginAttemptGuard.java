package com.example.hapjugakapi.config;

import com.example.hapjugakapi.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 실패 횟수를 세고, 너무 많으면 잠시 막는다.
 *
 * 왜 필요한가
 * - 로그인 실패 메시지를 「아이디를 확인해주세요」와 「비밀번호를 확인해주세요」로 나눴다.
 *   사용자에게는 훨씬 친절하지만, 대가가 있다. 메시지가 갈라지면
 *   "이 이메일이 가입돼 있는가"가 드러난다(user enumeration).
 * - 제한이 없으면 이메일 목록을 초당 수백 개씩 훑어 가입자 명단을 만들 수 있다.
 *   제한이 있으면 그게 불가능해진다. 그래서 메시지를 나눈 결정과 이 코드는 한 쌍이다.
 *
 * 두 가지 기준으로 센다
 * - 이메일: 한 계정을 노리고 비밀번호를 찍어보는 것을 막는다
 * - IP: 여러 계정을 훑는 것을 막는다
 * 둘 중 하나라도 걸리면 막는다.
 *
 * 왜 Redis 가 아니라 메모리인가
 * - 서버가 한 대다. 메모리로 충분하고, 의존성이 하나 줄어든다.
 * - 서버를 늘리는 순간 이 결정은 깨진다. 각 서버가 따로 세면 한도가 대수만큼 느슨해진다.
 *   그때는 Redis 로 옮긴다. 지금 미리 넣는 건 쓰지도 않을 복잡도다.
 * - 재시작하면 기록이 날아간다. 공격자가 재시작을 유발할 수 없으므로 문제 삼지 않는다.
 */
@Component
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    /** 한 계정에 대해 이 횟수를 넘겨 틀리면 잠근다. */
    private static final int EMAIL_MAX_FAILURES = 5;

    /** 한 IP 에서 이 횟수를 넘겨 틀리면 잠근다. 가족·회사처럼 한 IP 를 같이 쓰는 경우를 감안해 넉넉히 둔다. */
    private static final int IP_MAX_FAILURES = 20;

    /** 횟수를 세는 구간이자, 잠기는 시간. */
    private static final Duration WINDOW = Duration.ofMinutes(10);

    /**
     * 기억할 키의 상한.
     *
     * 이게 없으면 공격자가 매번 다른 이메일을 보내는 것만으로 서버 메모리를 채울 수 있다.
     * 막으려고 만든 장치가 공격 수단이 되면 안 된다.
     */
    private static final int MAX_KEYS = 50_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptGuard() {
        this(Clock.systemUTC());
    }

    /** 테스트에서 시간을 직접 움직이려고 둔 생성자. */
    LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * 불변 레코드로 둔 이유: ConcurrentHashMap.compute 안에서 통째로 교체하면
     * 따로 락을 걸지 않아도 "읽고 더하고 쓰는" 사이에 끼어들 틈이 없다.
     */
    private record Attempt(int count, Instant windowEnd, Instant blockedUntil) {

        boolean isBlocked(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        boolean isExpired(Instant now) {
            Instant last = blockedUntil != null && blockedUntil.isAfter(windowEnd) ? blockedUntil : windowEnd;
            return !now.isBefore(last);
        }
    }

    /** 로그인을 시도해도 되는지 본다. 막혀 있으면 예외를 던진다. */
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        ensureNotBlocked(emailKey(email), now);
        ensureNotBlocked(ipKey(ip), now);
    }

    /** 로그인이 실패했다. 두 기준 모두에 한 번씩 기록한다. */
    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        purgeIfCrowded(now);
        countUp(emailKey(email), EMAIL_MAX_FAILURES, now);
        countUp(ipKey(ip), IP_MAX_FAILURES, now);
    }

    /**
     * 로그인에 성공했다. 그 계정의 기록만 지운다.
     *
     * IP 기록은 남긴다. 계정 하나를 제대로 맞혔다고 해서
     * 그 전에 스무 번 틀린 사실이 없어지지는 않는다.
     */
    public void recordSuccess(String email) {
        attempts.remove(emailKey(email));
    }

    private void ensureNotBlocked(String key, Instant now) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || !attempt.isBlocked(now)) {
            return;
        }
        long seconds = Duration.between(now, attempt.blockedUntil()).getSeconds();
        if (seconds < 1) {
            seconds = 1;
        }
        long minutes = (seconds + 59) / 60;
        // 이메일 때문인지 IP 때문인지 알려주지 않는다. 알려주면 그 자체가 정보가 된다.
        throw new TooManyRequestsException(
                "로그인 시도가 너무 많습니다. " + minutes + "분 뒤에 다시 시도해주세요.", seconds);
    }

    private void countUp(String key, int max, Instant now) {
        if (attempts.size() >= MAX_KEYS && !attempts.containsKey(key)) {
            // 상한에 닿았으면 새 키는 더 만들지 않는다.
            // 이 지경이면 IP 기준 제한이 이미 걸려 있을 것이고, 메모리가 터지는 것보다 낫다.
            return;
        }
        Attempt updated = attempts.compute(key, (k, current) -> {
            if (current == null || now.isAfter(current.windowEnd())) {
                return new Attempt(1, now.plus(WINDOW), null);
            }
            if (current.isBlocked(now)) {
                return current;   // 이미 잠긴 동안에는 시간을 늘리지 않는다
            }
            int next = current.count() + 1;
            Instant blockedUntil = next >= max ? now.plus(WINDOW) : null;
            return new Attempt(next, current.windowEnd(), blockedUntil);
        });

        if (updated != null && updated.count() == max) {
            log.warn("로그인 시도 제한 - key={} 실패 {}회", maskKey(key), updated.count());
        }
    }

    /** 만료된 기록을 치운다. 평소에는 아무것도 하지 않고, 가득 찼을 때만 훑는다. */
    private void purgeIfCrowded(Instant now) {
        if (attempts.size() < MAX_KEYS) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + (ip == null ? "" : ip);
    }

    /** 로그에 이메일 주소를 통째로 남기지 않는다. */
    private static String maskKey(String key) {
        if (!key.startsWith("email:")) {
            return key;
        }
        String email = key.substring("email:".length());
        int at = email.indexOf('@');
        if (at <= 1) {
            return "email:***";
        }
        return "email:" + email.substring(0, Math.min(3, at)) + "***" + email.substring(at);
    }
}
