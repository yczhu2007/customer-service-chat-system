-- 为已有数据库补充可修改的展示昵称。
ALTER TABLE sys_user
    ADD COLUMN nickname VARCHAR(64) NULL COMMENT '展示昵称' AFTER username;

UPDATE sys_user
SET nickname = username
WHERE nickname IS NULL OR TRIM(nickname) = '';

ALTER TABLE sys_user
    MODIFY COLUMN nickname VARCHAR(64) NOT NULL COMMENT '展示昵称';
