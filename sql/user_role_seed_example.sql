-- 示例用户及角色初始化脚本
--
-- 使用方式：
-- 1. 先执行 user_ddl.sql；
-- 2. 再执行 rbac_ddl.sql；
-- 3. 使用 PasswordUtil 为三个账号生成真实PBKDF2密码哈希；
-- 4. 将下面三个占位符替换为真实哈希后再执行本脚本。
--
-- 严禁把明文密码直接写入 sys_user.password。

SET NAMES utf8mb4;

-- 将以下占位符替换成 PasswordUtil 生成的完整字符串：
--   ${U001_PASSWORD_HASH}
--   ${A001_PASSWORD_HASH}
--   ${ADMIN_PASSWORD_HASH}

-- INSERT INTO sys_user
--     (id, username, nickname, password, status)
-- VALUES
--     ('U001', 'user001', 'user001', '${U001_PASSWORD_HASH}', 'ENABLED'),
--     ('A001', 'agent001', 'agent001', '${A001_PASSWORD_HASH}', 'ENABLED'),
--     ('admin', 'admin', 'admin', '${ADMIN_PASSWORD_HASH}', 'ENABLED')
-- ON DUPLICATE KEY UPDATE
--     username = VALUES(username),
--     password = VALUES(password),
--     status = VALUES(status);


-- 用户数据创建成功后，为用户分配角色。
-- 下面语句只有在对应用户已经存在时才能执行。

-- INSERT INTO sys_user_role
--     (user_id, role_id)
-- VALUES
--     ('U001', 'R_USER'),
--     ('A001', 'R_AGENT'),
--     ('admin', 'R_ADMIN')
-- ON DUPLICATE KEY UPDATE
--     user_id = VALUES(user_id);
