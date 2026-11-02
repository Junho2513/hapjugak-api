package com.example.hapjugakapi.dto;

/**
 * "새로 만들 때만" 검사할 제약을 묶는 표식.
 *
 * 같은 요청 DTO 를 생성(POST)과 수정(PATCH)에 함께 쓰기 때문에 필요하다.
 * 수정에서는 null 이 "이 항목은 건드리지 않음"을 뜻하므로
 * 필수 검사를 그대로 걸면 수정이 불가능해진다.
 *
 *   생성: @Validated({ Default.class, OnCreate.class })  → 전부 검사
 *   수정: @Valid                                          → 필수 검사는 건너뜀
 *
 * 내용이 없는 인터페이스다. 제약에 이름표를 붙이는 용도로만 쓴다.
 */
public interface OnCreate {
}
