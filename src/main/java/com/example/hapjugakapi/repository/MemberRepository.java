package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 가입할 때 쓴다.
     *
     * 탈퇴한 회원은 email 이 NULL 이다. SQL 에서 NULL 은 어떤 값과도 같지 않으므로
     * 이 질의가 저절로 비켜간다. 「탈퇴자는 빼고」를 따로 적지 않아도
     * 같은 주소로 다시 가입할 수 있다.
     */
    boolean existsByEmail(String email);

    /** 로그인할 때 쓴다. 위와 같은 이유로 탈퇴한 회원은 걸리지 않는다. */
    Optional<Member> findByEmail(String email);

    /** 토큰에 적힌 회원이 아직 있는 사람인지. JwtAuthenticationFilter 가 쓴다. */
    boolean existsByIdAndDeletedAtIsNull(Long id);

    /** 회원 목록. 탈퇴한 사람은 보이지 않는다. */
    List<Member> findByDeletedAtIsNullOrderByIdAsc();
}