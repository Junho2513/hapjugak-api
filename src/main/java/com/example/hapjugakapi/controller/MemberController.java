package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.MemberRequest;
import com.example.hapjugakapi.dto.MemberResponse;
import com.example.hapjugakapi.service.MemberService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public MemberResponse create(@RequestBody MemberRequest request) {
        return memberService.create(request);
    }

    @GetMapping
    public List<MemberResponse> findAll() {
        return memberService.findAll();
    }

    @GetMapping("/{id}")
    public MemberResponse findById(@PathVariable Long id) {
        return memberService.findById(id);
    }

    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable Long id, @RequestBody MemberRequest request) {
        return memberService.update(id, request);
    }
}