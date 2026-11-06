-- 소셜 연결을 회원 행에서 떼어낸다.
--
-- ── 왜 ───────────────────────────────────────────────────────────────
-- 지금까지 연결은 member 테이블의 칸 두 개였다.
--
--   member.provider      GOOGLE
--   member.provider_id   1091...
--
-- 칸이 하나씩이므로 회원 한 명이 소셜 계정을 하나만 들 수 있다.
-- 그런데 실제로는 한 사람이 여럿을 가진다. 카카오 계정의 이메일과
-- 네이버 계정의 이메일이 같은 경우도 흔하다(카카오에 네이버 주소를 적어둔 사람).
--
-- 그 사람이 네이버로 가입한 뒤 카카오로 들어오면, 덮어쓰거나 막거나 둘 중
-- 하나였다. 덮어쓰면 네이버 로그인이 끊겨 자기 계정에 자기가 못 들어가고,
-- 막으면 카카오를 영영 못 쓴다. 칸이 하나인 한 답이 없다.
--
-- 연결을 행으로 만든다. 회원 하나에 연결 여럿이면 둘 다 쓸 수 있다.
--
--   member 31 ─┬─ social_account (KAKAO, 3847...)
--              └─ social_account (NAVER, abc9...)

CREATE TABLE social_account (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    linked_at   DATETIME(6)  NOT NULL,

    -- 하나의 소셜 계정은 한 회원에게만 붙는다.
    -- 이게 없으면 같은 카카오 계정으로 회원 둘을 만들 수 있고,
    -- 거의 동시에 들어온 두 요청이 둘 다 "없다"를 보고 둘 다 만들어 버린다.
    CONSTRAINT ux_social_identity UNIQUE (provider, provider_id),

    -- 한 회원은 제공자당 연결 하나.
    -- 구글 계정을 두 개 붙이는 것을 막는 뜻도 있지만, 더 중요한 쓰임이 있다.
    -- 같은 제공자로 다시 이으면 새 행이 생기는 게 아니라 있던 행이 고쳐진다.
    -- 낡은 고유번호가 저절로 지금 값으로 맞춰진다(아래 참고).
    CONSTRAINT ux_social_member_provider UNIQUE (member_id, provider),

    CONSTRAINT fk_social_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- ── 지금 있는 연결을 옮긴다 ──────────────────────────────────────────
-- LOCAL 은 소셜이 아니므로 뺀다. provider_id 가 비어 있는 행도 뺀다
-- (자체 가입, 그리고 탈퇴한 회원).
INSERT INTO social_account (member_id, provider, provider_id, linked_at)
SELECT id, provider, provider_id, created_at
  FROM member
 WHERE provider_id IS NOT NULL
   AND provider <> 'LOCAL';

-- ── 옮겨온 값 중 일부는 낡았다 ───────────────────────────────────────
-- Firestore 에서 이관할 때 provider_id 에 넣은 것이 구글의 sub 가 아니라
-- Firebase UID 였다. 둘은 다른 값이다. 그때는 이메일로 사람을 찾았기 때문에
-- 아무도 몰랐고, 찾는 기준을 고유번호로 바꾸자 27명 전원이 막혔다.
--
-- 여기서 고칠 수는 없다. 진짜 sub 는 그 사람이 구글로 로그인해 봐야 알 수 있다.
-- 대신 AuthService 가 "같은 제공자인데 고유번호가 다르면 지금 값으로 고친다"를
-- 하고, 위의 (member_id, provider) 유니크 덕분에 행이 늘지 않고 갱신된다.
-- 각자 한 번씩 로그인하면 저절로 정리된다.

-- ── member.provider_id 는 남겨둔다 ───────────────────────────────────
-- 사람을 찾는 기준은 이제 social_account 다. 그래도 칸은 지우지 않고,
-- 새 회원이 들어올 때 계속 채워 둔다.
--
-- 배포가 무중단(블루-그린)이라, 새 컨테이너가 뜨고 옛 컨테이너가 내려갈 때까지
-- 몇 십 초 동안 둘이 함께 돈다. 그동안 옛 코드는 member.provider_id 로 사람을
-- 찾는다. 지금 지우거나 비워두면 그 사이에 가입한 사람이 다음 로그인에서
-- 자기 계정을 못 찾는다.
--
-- 지우는 변경은 두 배포로 나눈다 — 이번에는 읽기를 멈추고, 다음 배포에서
-- 쓰기를 멈춘 뒤, 그다음에 칸을 뺀다. deploy/README.md 의 규칙 그대로다.
--
-- member.provider 는 계속 쓴다. 「처음에 어느 쪽으로 가입했는가」이고,
-- 자체 로그인 창에서 「카카오로 가입한 계정입니다」를 말할 때 쓰인다.
-- 연결이 여럿이어도 가입 경로는 하나다.
