package com.example.hapjugakapi.exception;

/**
 * 요청이 너무 잦아 잠시 막은 경우.
 *
 * 얼마나 기다려야 하는지를 같이 들고 다닌다.
 * 응답의 Retry-After 헤더로 나가고, 화면은 그 값으로 안내 문구를 만들 수 있다.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
