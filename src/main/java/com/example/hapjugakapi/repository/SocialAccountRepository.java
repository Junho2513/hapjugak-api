package com.example.hapjugakapi.repository;

import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.domain.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /**
     * 이 소셜 계정으로 전에 들어온 적이 있는가.
     *
     * 소셜 로그인에서 가장 확실한 기준이다. 제공자 안의 고유 번호는 바뀌지 않으므로,
     * 그쪽에서 이메일이나 이름을 바꿔도 같은 사람으로 찾아진다.
     *
     * 회원을 바로 쓸 것이므로 함께 읽어 온다. 안 그러면 이 질의 하나에
     * 회원을 읽는 질의가 뒤따라 붙는다.
     */
    @Query("select sa from SocialAccount sa join fetch sa.member "
            + "where sa.provider = :provider and sa.providerId = :providerId")
    Optional<SocialAccount> findWithMember(AuthProvider provider, String providerId);

    /** 이 회원이 이 제공자로 이미 이어둔 연결. 고유번호를 고칠 때 쓴다. */
    Optional<SocialAccount> findByMemberIdAndProvider(Long memberId, AuthProvider provider);

    /** 이 회원이 들고 있는 연결 전부. 화면에 「연결됨」을 보여줄 때 쓴다. */
    List<SocialAccount> findByMemberIdOrderByProviderAsc(Long memberId);

    /** 탈퇴할 때 함께 지운다. 남겨두면 그 소셜 계정으로 다시 가입할 수 없다. */
    void deleteByMemberId(Long memberId);
}
