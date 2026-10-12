-- users.role 컬럼을 MySQL ENUM('ADMIN','DEVELOPER') -> VARCHAR(20) 으로 변경
-- 이유: Hibernate ddl-auto=update 는 기존 ENUM 컬럼에 새 값(CLEANER)을 추가하지 않으므로
--       역할을 추가할 때마다 INSERT 가 'Data truncated' 로 실패한다. VARCHAR 로 바꾸면 Java enum 만 수정하면 된다.
-- 기존 데이터('ADMIN','DEVELOPER')는 문자열 그대로 유지된다.
ALTER TABLE users MODIFY COLUMN role VARCHAR(20) NOT NULL;
