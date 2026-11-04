-- 곡이 성립한 <순간>을 적어 둡니다.
--
-- 화면은 성립한 곡을 「먼저 성립한 것이 위」로 줄 세웁니다.
-- 올린 순서로 세우면, 나중에 올린 곡이 먼저 성립해도 저 아래에 남습니다.
-- "방금 각이 나왔는데 왜 밑에 있지?" 가 되는 자리입니다.
--
-- 성립이 풀리면 이 값을 지웁니다(애플리케이션에서). 그래야 다시 성립했을 때
-- 원래 자리로 돌아가지 않고 성립 곡들의 맨 아래로 붙습니다 — 사람이 본 순서와 맞습니다.
--
-- 화면에서만 기억할 수는 없습니다. 새로고침하거나 다른 사람이 보면 순서가 달라집니다.

ALTER TABLE song ADD COLUMN completed_at DATETIME(6) NULL AFTER created_at;

-- 이미 성립해 있는 곡들에는 올린 시각을 넣어 둡니다.
-- 언제 성립했는지는 남아 있지 않습니다. 지금 보이던 차례를 그대로 두는 쪽이
-- 화면이 갑자기 뒤바뀌지 않아 덜 놀랍습니다.
UPDATE song s
   SET s.completed_at = s.created_at
 WHERE EXISTS (SELECT 1 FROM slot sl WHERE sl.song_id = s.id)
   AND NOT EXISTS (SELECT 1 FROM slot sl WHERE sl.song_id = s.id AND sl.member_id IS NULL);
