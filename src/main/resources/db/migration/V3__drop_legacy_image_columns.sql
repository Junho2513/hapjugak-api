-- 이미지를 S3로 전부 옮긴 뒤, 옛 base64 컬럼을 제거한다.
--
-- "컬럼 추가 → 데이터 이관 → 컬럼 제거" 세 단계의 마지막이다.
-- V2에서 추가하고, 이관 러너로 옮기고, 여기서 지운다.
-- 중간 어느 시점에 서버가 떠도 화면이 비지 않도록 나눈 절차다.
--
-- 실행 전 조건 — 세 컬럼이 전부 NULL이어야 한다.
--   SELECT COUNT(photo_image) FROM member;        -- 0
--   SELECT COUNT(cover_image) FROM event;         -- 0
--   SELECT COUNT(hero_image)  FROM site_settings; -- 0
--
-- LONGTEXT를 비우기만 해서는 테이블 파일이 줄지 않는다(빈 공간으로 남는다).
-- 컬럼을 DROP 하면 테이블이 재구성되면서 실제로 줄어든다.

ALTER TABLE member        DROP COLUMN photo_image;
ALTER TABLE event         DROP COLUMN cover_image;
ALTER TABLE site_settings DROP COLUMN hero_image;
