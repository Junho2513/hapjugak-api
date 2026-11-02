package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.MemberResponse;
import com.example.hapjugakapi.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

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
    @PostMapping
    public ResponseEntity<MemberResponse> create(@RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @GetMapping
    public List<MemberResponse> findAll() {
        return memberService.findAll();
    }

    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal Long memberId) {
        return memberService.findById(memberId);
    }

    @GetMapping("/{id}")
    public MemberResponse findById(@PathVariable Long id) {
        return memberService.findById(id);
    }

    @PutMapping("/me")
    public MemberResponse updateMe(@AuthenticationPrincipal Long memberId,
                                   @RequestBody MemberRequest request) {
        return memberService.update(memberId, request);
    }

    /**
     * 삭제 후에는 돌려줄 표현이 없으므로 204 No Content 다.
     * 본문이 비어 있다는 것을 상태코드로 알리면 클라이언트가 응답을 파싱하려 하지 않는다.
     */
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
