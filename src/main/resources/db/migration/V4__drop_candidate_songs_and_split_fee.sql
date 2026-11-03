-- =============================================================================
-- V4 · 곡을 일정 안으로 (1/2)
--
-- 이 사이트는 오프라인 합주를 돕는 곳이다. 곡은 "언젠가 하고 싶은 것"이 아니라
-- "이 날 이 합주실에서 할 것"이다. 그런데 지금 구조는 곡이 일정 밖에 떠 있고,
-- 나중에 일정에 끼워 넣는 형태라 화면과 실제 쓰임이 어긋나 있었다.
--
-- 앞으로 곡은 반드시 일정에 속한다. 그 첫 단계다.
--
-- NOT NULL 을 여기서 걸지 않는 이유 ------------------------------------------
-- 지금 화면은 일정 없이도 곡을 올릴 수 있다. 컬럼을 먼저 NOT NULL 로 바꾸면
-- 새 화면이 배포되기 전까지 곡 올리기가 500 으로 죽는다.
-- 그래서 V2→V3(이미지 이관) 때와 같은 순서를 쓴다.
--
--   V4(여기)  떠다니던 곡을 정리한다. 스키마는 아직 NULL 을 허용한다.
--   그다음    화면이 일정 안에서만 곡을 올리도록 바뀐다.
--   V5        그때 event_id 를 NOT NULL 로 조인다.
--
-- 중간 어느 시점에 서버가 떠도 사이트가 멀쩡하다.
-- =============================================================================


-- 1. 일정에 들어가지 않은 곡을 지운다 ------------------------------------------
--
-- 2026-11-03 기준 66곡. 자리 336개 중 55개는 이미 사람이 잡아둔 상태였다.
-- 남겨두면 "어느 합주에서 하는지 모르는 곡"이 그대로 남는데,
-- 새 구조에는 그것을 둘 자리가 없다. 다시 올리고 다시 잡는 쪽을 택했다.
--
-- 지우는 순서가 중요하다. 참조하는 쪽부터 지워야 외래키가 막지 않는다.
--   activity_log.song_id → slot.song_id → song
-- activity_log 는 song_id 가 NOT NULL 이라 곡만 지울 수가 없다.
-- 없어진 곡에 대한 기록이므로 함께 지운다.

DELETE FROM activity_log WHERE song_id IN (SELECT id FROM song WHERE event_id IS NULL);
DELETE FROM slot         WHERE song_id IN (SELECT id FROM song WHERE event_id IS NULL);
DELETE FROM song         WHERE event_id IS NULL;


-- 2. 참가비를 숫자와 안내 문구로 나눈다 ----------------------------------------
--
-- 지금은 varchar(200) 한 칸이라 "15,000", "만오천", "79795632달러" 가 전부 통과한다.
-- 사람이 읽기엔 괜찮지만 정렬도 필터도 통계도 불가능하다.
-- "참가비 2만원 이하인 합주"를 찾을 방법이 없다.
--
-- 옮기는 방식 —
-- 숫자로 읽히는 것만 fee_amount 로 보내고, 나머지는 fee_note 에 원문 그대로 남긴다.
-- 억지로 숫자를 뽑아내지 않는다. "대관비 N빵"에서 숫자를 짜내면 틀린 값이 들어간다.
-- 애매한 것은 사람이 다음에 수정할 때 채우게 둔다.

ALTER TABLE event ADD COLUMN fee_amount INT          NULL AFTER fee;
ALTER TABLE event ADD COLUMN fee_note   VARCHAR(200) NULL AFTER fee_amount;

-- 먼저 전부 원문을 안내 문구로 옮긴다. 이 단계에서 잃는 것은 없다.
UPDATE event
   SET fee_note = fee
 WHERE fee IS NOT NULL AND fee <> '';

-- 그중 "15,000" "15000원" 처럼 숫자로만 읽히는 것은 금액으로 승격시킨다.
-- 쉼표와 '원'만 떼어낸 결과가 숫자뿐일 때만 해당한다.
-- 자릿수 제한은 INT 넘침 방지용이다(최대 2,147,483,647).
UPDATE event
   SET fee_amount = CAST(TRIM(REPLACE(REPLACE(fee, ',', ''), '원', '')) AS UNSIGNED),
       fee_note   = NULL
 WHERE TRIM(REPLACE(REPLACE(fee, ',', ''), '원', '')) REGEXP '^[0-9]+$'
   AND CHAR_LENGTH(TRIM(REPLACE(REPLACE(fee, ',', ''), '원', ''))) <= 9;

ALTER TABLE event DROP COLUMN fee;


-- 3. 「정원」을 「연습실 수용 인원」으로 ------------------------------------------
--
-- 이름이 거짓말을 하고 있었다. 정원 29명이라고 적어도 아무것도 막지 않는다.
-- 막을 수도 없다 — 파트 자리는 곡마다 붙는데 정원은 일정 단위라 둘이 따로 논다.
--
-- 실제로 막는 장치로 만드는 선택지도 있었지만, 늦게 온 사람이 자리를 못 잡는
-- 쪽이 더 나쁘다고 봤다. 연습실이 몇 명 들어가는 방인지 알려주는 안내값이
-- 원래 하던 일이고, 이름을 거기에 맞춘다.

ALTER TABLE event CHANGE COLUMN capacity room_capacity INT NOT NULL;
