package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.MemberPublicResponse;
import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.MemberResponse;
import com.example.hapjugakapi.dto.OnCreate;
import com.example.hapjugakapi.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "회원", description = "가입 · 조회 · 내 정보 수정 · 삭제")
@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * 회원가입.
     *
     * 201 Created 와 함께 Location 헤더로 만들어진 자원의 주소를 알려준다.
     * 200 으로 돌려주면 "요청은 성공했다"까지만 전달되고,
     * 무엇이 새로 생겼는지는 본문을 열어봐야 알 수 있다.
     */
    @Operation(summary = "회원가입", description = "누구나 호출할 수 있습니다. 성공하면 Location 헤더에 만들어진 회원의 주소가 담깁니다.")
    @ApiResponse(responseCode = "201", description = "회원이 만들어졌습니다")
    @ApiResponse(responseCode = "400", description = "입력값이 규칙에 맞지 않습니다")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일입니다")
    @PostMapping
    public ResponseEntity<MemberResponse> create(
            @Validated({Default.class, OnCreate.class}) @RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @Operation(summary = "회원 목록", description = "관리자만 볼 수 있습니다.")
    @GetMapping
    public List<MemberResponse> findAll() {
        return memberService.findAll();
    }

    @Operation(summary = "내 정보", description = "로그인한 본인의 정보입니다. 여기에만 이메일이 들어 있습니다.")
    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal Long memberId) {
        return memberService.findById(memberId);
    }

    /**
     * 남의 프로필 보기. 로그인한 사람만 볼 수 있고 이메일은 들어 있지 않다.
     *
     * 전에는 누구나 부를 수 있었고 응답에 이메일이 그대로 담겨 있었다.
     * id 를 1부터 세어가며 부르면 회원 전체의 이메일을 수집할 수 있었다.
     * 목록(GET /members)을 관리자로 막아둔 것이 아무 의미가 없는 상태였다.
     *
     * 본인 정보는 GET /members/me 를 쓴다.
     *
     * 200 을 직접 적어둔 이유: @ApiResponse 를 하나라도 쓰면 springdoc 이
     * 자동으로 넣어주던 성공 응답이 빠진다. 그대로 두면 문서가 실패 경우만 보여준다.
     */
    @Operation(summary = "회원 한 명 조회",
            description = "로그인한 사람만 볼 수 있습니다. 이메일·역할은 돌려주지 않습니다. "
                    + "본인 정보는 /members/me 를 쓰세요.")
    @ApiResponse(responseCode = "200", description = "공개 프로필",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = MemberPublicResponse.class)))
    @ApiResponse(responseCode = "401", description = "로그인이 필요합니다")
    @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없습니다")
    @GetMapping("/{id}")
    public MemberPublicResponse findById(@PathVariable Long id) {
        return memberService.findPublicById(id);
    }

    @Operation(summary = "내 정보 수정", description = "보낸 항목만 바뀝니다. 비워 보낸 항목은 그대로 둡니다.")
    @PutMapping("/me")
    public MemberResponse updateMe(@AuthenticationPrincipal Long memberId,
                                   @Valid @RequestBody MemberRequest request) {
        return memberService.update(memberId, request);
    }

    /**
     * 삭제 후에는 돌려줄 표현이 없으므로 204 No Content 다.
     * 본문이 비어 있다는 것을 상태코드로 알리면 클라이언트가 응답을 파싱하려 하지 않는다.
     */
    @Operation(summary = "회원 삭제", description = "본인 또는 관리자만 할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "삭제되었습니다")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal Long memberId) {
        memberService.delete(id, memberId);
    }

    /**
     * 현재 요청 주소 뒤에 /{id} 를 붙여 Location 을 만든다.
     * context-path(/api)가 바뀌어도 따라가므로 주소를 코드에 적어두지 않는다.
     */
    private static URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
