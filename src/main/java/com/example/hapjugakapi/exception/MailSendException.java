package com.example.hapjugakapi.exception;

/**
 * 메일을 보내지 못한 경우.
 *
 * 우리 잘못도 사용자 잘못도 아니고, 기대던 바깥 서비스(SES)가 응답하지 않은 것이다.
 * 그래서 400 도 500 도 아닌 503 으로 내보낸다 - "지금은 안 되니 잠시 뒤에"라는 뜻이다.
 */
public class MailSendException extends RuntimeException {

    public MailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
