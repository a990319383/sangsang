-- db模式加密需要的表
CREATE TABLE `demo_db_user`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT,
    `user_name`   varchar(100) DEFAULT NULL COMMENT '用户名',
    `login_name`  varchar(100) DEFAULT NULL COMMENT '登录名',
    `login_pwd`   varchar(100) DEFAULT NULL COMMENT '登录密码',
    `phone`       varchar(50)  DEFAULT NULL COMMENT '电话号码（密文）',
    `org_seq`     varchar(100) DEFAULT NULL COMMENT '组织的全路径（上级的上级权限/上级权限/本级权限）',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`)
) COMMENT='sangsang-db模式加密的用户表';

-- pojo模式加密需要的表
CREATE TABLE `demo_pojo_user`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT,
    `user_name`   varchar(100) DEFAULT NULL COMMENT '用户名',
    `login_name`  varchar(100) DEFAULT NULL COMMENT '登录名',
    `login_pwd`   varchar(100) DEFAULT NULL COMMENT '登录密码',
    `phone`       varchar(50)  DEFAULT NULL COMMENT '电话号码（密文）',
    `org_seq`     varchar(100) DEFAULT NULL COMMENT '组织的全路径（上级的上级权限/上级权限/本级权限）',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`)
) COMMENT='sangsang-pojo模式加密的用户表';

-- 数据权限隔离需要的表
CREATE TABLE `demo_isolation_user`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT,
    `user_name`   varchar(100) DEFAULT NULL COMMENT '用户名',
    `login_name`  varchar(100) DEFAULT NULL COMMENT '登录名',
    `login_pwd`   varchar(100) DEFAULT NULL COMMENT '登录密码',
    `phone`       varchar(50)  DEFAULT NULL COMMENT '电话号码',
    `org_seq`     varchar(100) DEFAULT NULL COMMENT '组织的全路径（上级的上级权限/上级权限/本级权限）',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`)
) COMMENT='sangsang-isolation的用户表';

-- 异构数据库语法转换需要的表
CREATE TABLE `demo_transformation_user`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT,
    `user_name`   varchar(100) DEFAULT NULL COMMENT '用户名',
    `login_name`  varchar(100) DEFAULT NULL COMMENT '登录名',
    `login_pwd`   varchar(100) DEFAULT NULL COMMENT '登录密码',
    `phone`       varchar(50)  DEFAULT NULL COMMENT '电话号码（密文）',
    `org_seq`     varchar(100) DEFAULT NULL COMMENT '组织的全路径（上级的上级权限/上级权限/本级权限）',
    `create_time` datetime     DEFAULT NULL,
    `update_time` datetime     DEFAULT NULL,
    PRIMARY KEY (`id`)
) COMMENT='sangsang-transformation用户表';