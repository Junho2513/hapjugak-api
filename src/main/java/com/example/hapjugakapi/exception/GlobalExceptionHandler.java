package com.example.hapjugakapi.exception;

import com.example.hapjugakapi.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "다른 사람이 먼저 자리를 잡았습니다. 새로고침 후 다시 시도해주세요."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, e.getMessage()));
    }

    /**
     * 요청이 너무 잦아 잠시 막은 경우.
     *
     * Retry-After 를 같이 보낸다. "다시 시도해보세요"만 주고 언제인지 안 알려주면
     * 클라이언트는 계속 두드리는 수밖에 없다.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of(429, e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, e.getMessage()));
    }

    /**
     * @Valid 검증 실패.
     *
     * 어떤 항목이 왜 틀렸는지를 그대로 돌려준다.
     * "입력값이 올바르지 않습니다" 한 줄만 주면 사용자는 어디를 고쳐야 할지 모른다.
     * 제약마다 적어둔 한국어 메시지가 여기로 모인다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
        if (message.isBlank()) {
            message = "입력값이 올바르지 않습니다.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, message));
    }

    /**
     * 본문이 JSON 으로 읽히지 않는 경우.
     * 잘못 만든 요청이므로 400 이지, 서버 잘못이 아니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "요청 형식이 올바르지 않습니다."));
    }

    /**
     * DB 제약조건 위반. 대개는 "다른 데이터가 이걸 참조하고 있다"는 뜻이다.
     *
     * 이 자리가 비어 있어서, 곡을 지울 때 활동 로그가 걸려 있으면
     * 원인을 알 수 없는 500 이 나갔다. 사용자는 "삭제하지 못했습니다"만 보고
     * 왜인지 알 방법이 없었다.
     *
     * 상세 내용은 로그에만 남긴다. 외부에 테이블 이름이나 제약조건 이름을
     * 그대로 흘리면 DB 구조를 알려주는 셈이 된다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 제약조건 위반", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "다른 데이터가 이 항목을 참조하고 있어 처리할 수 없습니다."));
    }

    /**
     * 메일 발송 실패.
     *
     * 우리 코드가 틀린 것도, 사용자가 잘못 보낸 것도 아니다.
     * 기대던 바깥 서비스가 응답하지 않은 것이라 500 이 아니라 503 이 맞다.
     * 실패한 이유는 MailService 에서 이미 로그에 남겼다.
     */
    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<ErrorResponse> handleMailSend(MailSendException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(ErrorResponse.of(503, "메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."));
    }

    /**
     * Spring 이 이미 상태코드를 정해둔 예외는 그 상태코드를 지킨다.
     *
     * 아래 Exception 핸들러만 있으면 없는 주소 요청(404)까지 500 으로 바뀐다.
     * 더 구체적인 이 핸들러가 먼저 잡아서 그걸 막는다.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleSpringError(ErrorResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        String detail = e.getBody().getDetail();
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(),
                        detail != null ? detail : "요청을 처리할 수 없습니다."));
    }

    /**
     * 위 어느 것에도 걸리지 않은 예외.
     *
     * 스택트레이스는 서버 로그에만 남기고 응답에는 싣지 않는다.
     * 예외 메시지에는 쿼리문이나 파일 경로가 섞여 나오는 일이 많다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "서버에서 문제가 생겼습니다. 잠시 후 다시 시도해주세요."));
    }
}
