-- 옛 LONGTEXT 컬럼은 건드리지 않고 새 컬럼만 추가한다.
--
-- 한 번에 바꾸지 않는 이유 —
-- 컬럼을 갈아치우면 "데이터를 옮기는 동안" 이미지가 비는 구간이 생긴다.
-- 추가해두고, 옮기고, 다 옮긴 뒤에 지우면 어느 시점에 서버가 떠도 화면이 멀쩡하다.
-- 옛 컬럼은 이관이 끝난 뒤 V3에서 제거한다.
--
-- event와 site_settings 컬럼은 아직 엔티티에 없지만 같이 만들어둔다.
-- 마이그레이션을 세 번 나눌 이유가 없고, validate는 엔티티에 없는 컬럼을 문제 삼지 않는다.

ALTER TABLE member        ADD COLUMN photo_url VARCHAR(500) NULL;
ALTER TABLE event         ADD COLUMN cover_url VARCHAR(500) NULL;
ALTER TABLE site_settings ADD COLUMN hero_url  VARCHAR(500) NULL;
