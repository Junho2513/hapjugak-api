-- =============================================================================
-- V1 · 기준선 (baseline)
--
-- 2026-10-31 시점에 운영 중이던 스키마를 그대로 옮겨 적은 것입니다.
-- (mysqldump --no-data 결과에서 AUTO_INCREMENT 현재값만 제거하고,
--  외래키 의존 순서대로 재배치했습니다.)
--
-- 운영 DB에는 이미 이 구조와 2개월 반치 데이터가 들어 있습니다.
-- 그래서 Flyway는 baseline-on-migrate 설정에 따라 이 파일을
-- "이미 적용됨"으로 기록만 하고 실행하지 않습니다.
-- 실제로 실행되는 곳은 빈 DB뿐입니다 — 새 개발 환경과 CI 테스트.
--
-- 제약조건 이름(FK..., UK...)이 해시처럼 생긴 것은 Hibernate가
-- 자동 생성한 이름이기 때문입니다. 보기 좋게 바꾸고 싶지만 그대로 둡니다.
-- 운영 DB에 이미 이 이름으로 존재하므로, 여기서 바꾸면 운영과 신규 환경의
-- 스키마가 서로 갈라집니다. 기준선은 "예쁜 모습"이 아니라 "실제 모습"입니다.
-- =============================================================================


-- 회원 -------------------------------------------------------------------
-- password가 NULL 가능한 이유: 구글 가입 회원은 비밀번호를 저장하지 않습니다.
-- photo_image가 longtext인 이유: 프로필 사진이 URL이 아니라 base64입니다.
--   (객체 스토리지로 옮기는 것이 과제로 남아 있습니다)
CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `area` varchar(255) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `nickname` varchar(20) NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `photo_image` longtext,
  `provider` enum('GOOGLE','LOCAL') NOT NULL,
  `provider_id` varchar(255) DEFAULT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmbmcqelty0fbrvxp1q58dn57t` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 회원이 다루는 악기 (N개) -------------------------------------------------
CREATE TABLE `member_instrument` (
  `member_id` bigint NOT NULL,
  `instrument` enum('ACOUSTIC','BASS','DRUM','GUITAR','KEY','VOCAL') DEFAULT NULL,
  UNIQUE KEY `UKevdaohfg4alorb6jopx6omqik` (`member_id`,`instrument`),
  CONSTRAINT `FKek29vvrghcb317y3i3x2guukx` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 합주 일정 ---------------------------------------------------------------
CREATE TABLE `event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `afterparty` varchar(40) DEFAULT NULL,
  `capacity` int NOT NULL,
  `completed` bit(1) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `cover_image` longtext,
  `created_at` datetime(6) NOT NULL,
  `date` date DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `fee` varchar(200) DEFAULT NULL,
  `genre` varchar(40) DEFAULT NULL,
  `max_songs_per_user` int NOT NULL,
  `region` varchar(30) DEFAULT NULL,
  `room` varchar(60) DEFAULT NULL,
  `studio` varchar(100) DEFAULT NULL,
  `time_end` time DEFAULT NULL,
  `time_start` time DEFAULT NULL,
  `title` varchar(60) NOT NULL,
  `visibility` enum('PRIVATE','PUBLIC') NOT NULL,
  `host_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtdhe5swpf0i976vyjmov0ek2h` (`host_id`),
  CONSTRAINT `FKtdhe5swpf0i976vyjmov0ek2h` FOREIGN KEY (`host_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 그 합주에서 기본으로 잡는 파트 구성 --------------------------------------
CREATE TABLE `event_default_part` (
  `event_id` bigint NOT NULL,
  `part` enum('ACOUSTIC','BASS','DRUM','GUITAR1','GUITAR2','KEY1','KEY2','VOCAL') DEFAULT NULL,
  UNIQUE KEY `UKjnycmponyhfd0pv434byeep4o` (`event_id`,`part`),
  CONSTRAINT `FK7gt34blc9k5j8g7mt86jbq5mp` FOREIGN KEY (`event_id`) REFERENCES `event` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 곡 ---------------------------------------------------------------------
-- event_id가 NULL이면 아직 일정에 배정되지 않은 "곡 후보"입니다.
CREATE TABLE `song` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artist` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `youtube_url` varchar(500) DEFAULT NULL,
  `event_id` bigint DEFAULT NULL,
  `proposer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKq8je05ungqv6ci6anmgpocktl` (`event_id`),
  KEY `FKjucxo23uwars7umtcomjq7p5t` (`proposer_id`),
  CONSTRAINT `FKjucxo23uwars7umtcomjq7p5t` FOREIGN KEY (`proposer_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKq8je05ungqv6ci6anmgpocktl` FOREIGN KEY (`event_id`) REFERENCES `event` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 파트 자리 ---------------------------------------------------------------
-- version 컬럼이 낙관적 락의 핵심입니다. 이 컬럼이 없으면 두 명이 동시에
-- 같은 자리를 잡았을 때 나중 요청이 앞의 요청을 조용히 덮어씁니다.
CREATE TABLE `slot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `part` enum('ACOUSTIC','BASS','DRUM','GUITAR1','GUITAR2','KEY1','KEY2','VOCAL') NOT NULL,
  `version` bigint NOT NULL,
  `member_id` bigint DEFAULT NULL,
  `song_id` bigint NOT NULL,
  `open_to_handover` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnrelfkra1k69qq5rawt3sv4ag` (`member_id`),
  KEY `FK6luwekwsdc3r2gg7s0qh0p45f` (`song_id`),
  CONSTRAINT `FK6luwekwsdc3r2gg7s0qh0p45f` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`),
  CONSTRAINT `FKnrelfkra1k69qq5rawt3sv4ag` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 활동 기록 ---------------------------------------------------------------
-- actor_id(FK)가 있는데도 actor_name을 따로 저장하는 것은 의도한 비정규화입니다.
-- 회원이 닉네임을 바꿔도 과거 기록의 이름은 그대로 남아야 합니다.
-- type을 enum이 아니라 varchar로 둔 것도 의도입니다. 로그 종류는 기능과 함께
-- 늘어나는데, enum이면 새 값을 만난 과거 데이터를 읽다가 애플리케이션이 죽습니다.
CREATE TABLE `activity_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_name` varchar(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(200) DEFAULT NULL,
  `part` enum('ACOUSTIC','BASS','DRUM','GUITAR1','GUITAR2','KEY1','KEY2','VOCAL') DEFAULT NULL,
  `song_title` varchar(100) DEFAULT NULL,
  `target_name` varchar(20) DEFAULT NULL,
  `type` varchar(30) NOT NULL,
  `actor_id` bigint DEFAULT NULL,
  `song_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnnys9e036wxawk8i4pf8g7htr` (`actor_id`),
  KEY `FKnx5im4g201s1tvt61obg03bov` (`song_id`),
  CONSTRAINT `FKnnys9e036wxawk8i4pf8g7htr` FOREIGN KEY (`actor_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKnx5im4g201s1tvt61obg03bov` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- 사이트 전역 설정 ---------------------------------------------------------
-- 행이 하나만 존재합니다 (id = 1 고정, AUTO_INCREMENT 없음).
-- 콜레이션이 다른 테이블과 다른 것은 이 테이블만 Docker로 옮긴 뒤에
-- 생성됐기 때문입니다 (컨테이너는 collation-server=utf8mb4_unicode_ci).
-- 실제 운영 DB가 이 상태라 기준선도 그대로 적습니다.
CREATE TABLE `site_settings` (
  `id` bigint NOT NULL,
  `hero_image` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
