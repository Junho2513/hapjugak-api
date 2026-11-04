-- 자리에 「보이는 이름」을 따로 둡니다.
--
-- 지금까지는 종류(enum)가 곧 이름이었습니다. 그래서 두 가지를 못 했습니다.
--   1. 같은 파트를 여러 명 모집하기 — 보컬이 둘인 곡을 적을 방법이 없었습니다.
--      (기타·키보드만 GUITAR1/GUITAR2 처럼 번호를 enum 에 박아 억지로 두 자리를 만들었습니다)
--   2. 목록에 없는 파트 적기 — 색소폰, 퍼커션, 코러스 같은 것이 들어갈 자리가 없었습니다.
--
-- 이름(label)과 종류(part)를 나눕니다.
--   label : 화면에 보이는 그대로. 「보컬1」, 「기타2」, 「색소폰」. 사람이 정합니다.
--   part  : 악기가 겹치는지 판단할 때만 씁니다. 직접 적은 파트에는 없을 수 있습니다.
--           (한 사람이 같은 곡에서 기타와 베이스를 동시에 잡을 수는 없다는 규칙)

-- ── 1. 이름 칸을 만들고, 지금 있는 자리의 이름을 채웁니다 ──────────────
ALTER TABLE slot ADD COLUMN label VARCHAR(30) NULL AFTER part;

UPDATE slot SET label = CASE part
        WHEN 'VOCAL'    THEN '보컬'
        WHEN 'ACOUSTIC' THEN '통기타'
        WHEN 'GUITAR1'  THEN '기타1'
        WHEN 'GUITAR2'  THEN '기타2'
        WHEN 'BASS'     THEN '베이스'
        WHEN 'DRUM'     THEN '드럼'
        WHEN 'KEY1'     THEN '키보드1'
        WHEN 'KEY2'     THEN '키보드2'
        ELSE part
    END
 WHERE label IS NULL;

ALTER TABLE slot MODIFY COLUMN label VARCHAR(30) NOT NULL;

-- ── 2. 보여줄 차례 ────────────────────────────────────────────────────
-- 자리를 적은 순서대로 보여줍니다. 지금 있는 것은 보기 좋던 기존 순서를 그대로 둡니다.
ALTER TABLE slot ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE slot
   SET sort_order = FIELD(part, 'VOCAL','ACOUSTIC','GUITAR1','GUITAR2','BASS','DRUM','KEY1','KEY2');

-- ── 3. 종류는 이제 없을 수 있습니다 ───────────────────────────────────
-- enum 컬럼이면 「색소폰」을 넣는 순간 거절당합니다. varchar 로 바꾸고 비워둘 수 있게 합니다.
ALTER TABLE slot MODIFY COLUMN part VARCHAR(20) NULL;

-- ── 3-2. 한 곡에 하나뿐인 자리는 번호를 뗍니다 ────────────────────────
-- 「기타1」만 있고 「기타2」가 없으면 그냥 「기타」입니다.
-- 번호는 둘 이상일 때만 뜻이 있습니다. (키보드1, 통기타도 마찬가지)
UPDATE slot s
  JOIN (SELECT song_id,
               LEFT(label, CHAR_LENGTH(label) - 1) AS base,
               COUNT(*) AS c
          FROM slot
         WHERE label REGEXP '[0-9]$'
         GROUP BY song_id, base) g
    ON g.song_id = s.song_id
   AND g.base = LEFT(s.label, CHAR_LENGTH(s.label) - 1)
   SET s.label = g.base
 WHERE g.c = 1
   AND s.label REGEXP '[0-9]$';

-- ── 4. 한 곡에 같은 이름의 자리가 둘이면 안 됩니다 ────────────────────
-- 화면은 자리를 이름으로 찾습니다. 이름이 겹치면 어느 쪽인지 가릴 수가 없습니다.
CREATE UNIQUE INDEX ux_slot_song_label ON slot (song_id, label);

-- ── 5. 활동 기록에도 직접 적은 파트 이름이 들어갑니다 ─────────────────
ALTER TABLE activity_log MODIFY COLUMN part VARCHAR(30) NULL;
