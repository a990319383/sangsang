CREATE TABLE `demo_user`
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
) COMMENT='sangsang-demo的用户表';