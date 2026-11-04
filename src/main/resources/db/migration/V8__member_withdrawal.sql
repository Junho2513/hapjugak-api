-- 탈퇴, 그리고 V4 가 미뤄둔 숙제 하나.
--
-- ── 탈퇴를 어떻게 할 것인가 ───────────────────────────────────────────
-- 회원 행을 DELETE 하면 안 됩니다. member.id 를 가리키는 외래키가 네 군데입니다.
--   event.host_id (NOT NULL) · song.proposer_id (NOT NULL) · slot.member_id · activity_log.actor_id
-- ON DELETE 규칙이 하나도 없으므로, 합주를 하나라도 열었거나 자리를 하나라도
-- 잡아본 사람을 지우면 DB 가 거부합니다. 실사용자 중 이 조건을 피하는 사람은 거의 없습니다.
-- CASCADE 를 걸면 더 나쁩니다 — 한 사람이 나가면서 남의 합주와 남의 곡 기록까지
-- 같이 사라집니다.
--
-- 그래서 행은 남기고, 그 행에서 "그 사람을 가리키는 정보"를 전부 비웁니다.
-- 남는 것은 id 와 가입일, 그리고 「탈퇴한 사용자」라는 이름뿐입니다.
-- 이메일·비밀번호·구글 식별자·지역·소개·사진·악기는 DB 에서 사라집니다.
--
-- ── 같은 메일로 다시 가입할 수 있어야 합니다 ──────────────────────────
-- email 에 UNIQUE 가 걸려 있고 NOT NULL 입니다. 탈퇴한 사람의 메일을 그대로 두면
-- 그 사람은 다시 가입할 수 없습니다. 「deleted-17@…」 같은 가짜 주소로 덮는 방법도
-- 있지만, 그건 지운 게 아니라 바꿔치기한 것이고 id 를 통해 누구였는지도 남습니다.
--
-- NULL 로 비웁니다. MySQL 의 UNIQUE 는 NULL 을 여러 개 허용하므로
-- 탈퇴자가 몇 명이든 걸리지 않습니다. 그리고 SQL 에서 NULL 은 어떤 값과도
-- 같지 않으므로, findByEmail / existsByEmail 이 탈퇴한 행을 저절로 비켜갑니다.
-- 로그인과 재가입 쪽에 "탈퇴자는 빼고" 를 따로 적지 않아도 됩니다.
ALTER TABLE member MODIFY COLUMN email VARCHAR(255) NULL;

-- 언제 나갔는지. 이 칸이 차 있으면 탈퇴한 회원입니다.
-- 화면에서 거를 때 "email 이 NULL 이면" 보다 뜻이 분명합니다.
ALTER TABLE member ADD COLUMN deleted_at DATETIME(6) NULL;


-- ── V4 가 미뤄둔 것 ───────────────────────────────────────────────────
-- V4 주석에 "V5 에서 event_id 를 NOT NULL 로 조인다" 고 적어두고,
-- V5 가 초대키로 가면서 그대로 남았습니다.
--
-- 곡이 일정 밖에 떠 있던 시절의 잔재입니다. 지금 코드는 곡에 일정이 반드시 있다고
-- 보고 짜여 있습니다(SongService.createIn 만 곡을 만듭니다). 가정이 코드에만 있고
-- DB 에는 없으면, 언젠가 그 가정이 틀어졌을 때 NullPointerException 으로 만납니다.
--
-- V4 가 이미 지웠지만, 그 뒤로 끼어든 것이 있을 수 있으니 한 번 더 훑고 조입니다.
DELETE FROM activity_log WHERE song_id IN (SELECT id FROM song WHERE event_id IS NULL);
DELETE FROM slot         WHERE song_id IN (SELECT id FROM song WHERE event_id IS NULL);
DELETE FROM song         WHERE event_id IS NULL;

ALTER TABLE song MODIFY COLUMN event_id BIGINT NOT NULL;
