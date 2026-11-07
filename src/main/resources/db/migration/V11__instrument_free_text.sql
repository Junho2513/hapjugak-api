-- 회원이 고를 수 있는 악기를 여섯 가지 enum 으로 묶어 두었다.
-- 색소폰, 트럼펫, 카혼처럼 밴드에서 흔히 쓰는 파트가 그 안에 없어서
-- 적을 방법 자체가 없었다.
--
-- enum 에 값을 추가하는 방식은 택하지 않았다. 새 파트가 나올 때마다
-- 마이그레이션을 한 번씩 더 돌려야 하고, 그 사이에는 여전히 적을 수 없다.
--
-- 곡의 파트(slot.label)는 처음부터 자유 문자열이었다. 회원 쪽만 묶여 있던 것이라
-- 같은 모양으로 맞춘다.
-- NOT NULL 로 바꾸기 전에 빈 행을 먼저 치운다.
-- (member_id, instrument) 유니크 키가 있어서 생길 일은 없지만,
-- 한 줄이라도 NULL 이 있으면 아래 ALTER 가 통째로 실패하면서 배포가 멈춘다.
DELETE FROM `member_instrument` WHERE `instrument` IS NULL;

-- ENUM → VARCHAR 변환은 MySQL 이 enum 의 「문자열 값」을 그대로 옮긴다.
-- 기존 행의 'VOCAL', 'GUITAR' … 가 같은 글자로 남는다.
ALTER TABLE `member_instrument`
  MODIFY COLUMN `instrument` VARCHAR(30) NOT NULL;
