package com.sangsang.mockentity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统组织 entity
 *
 * @author dubing
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_org")
public class SysOrgEntity extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 父级id
     */
    private Long parentId;

    /**
     * 父级名称
     */
    private String parentName;

    /**
     * 名称
     */
    private String name;

    /**
     * 简称
     */
    private String shortName;

    /**
     * 企业类型 1 集团 2 电厂 3 承运商
     */
    private Integer type;

    /**
     * 联系人
     */
    private String contact;

    /**
     * 联系人电话
     */
    private String phone;

    /**
     * 企业邮箱
     */
    private String email;

    /**
     * 失效时间
     */
    private Date availableDate;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 详细地址
     */
    private String address;

    /**
     * s商业编码
     */
    private String businessNum;

    /**
     * 介绍、描述
     */
    private String introduce;

    /**
     * 企业状态 1启用 0停用
     */
    private Integer status;

    /**
     * 逻辑删除标识1 已删除 0 未删除
     */
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    /**
     * edi服务企业code
     */
    private String enterpriseCode;

    /**
     * 企业微信appid
     */
    private String appId;

    /**
     * 企业微信appSecret
     */
    private String appSecret;

    /**
     * 道路运输经营许可证号
     */
    private String roadTransPermitNumber;

    /**
     * 道路运输经营许可证照片
     */
    private String roadTransPermitPic;

    /**
     * 所属检测平台
     */
    private String wcjcPlatformCode;

    /**
     * 短信签名
     */
    private String sign;

    /**
     * 短信签名编码
     */
    private String extNo;

    /**
     * 开通认证状态 1开通 0关闭
     */
    private Byte openAuthFlag;

    /**
     * 承运商认证开关0开通，1未开通
     */
    private Byte openCarrierAuth;

    /**
     * 调用调用增强服务唯一标识appid
     */
    private String boostAppid;

    /**
     * 调用方签名秘钥、请求参数加密秘钥、返回值解密秘钥
     */
    private String boostAppsecret;

    /**
     * 调用方签名秘钥、请求参数加密秘钥、返回值解密秘钥
     */
    private Integer appFaceAuth;

    /**
     * 公司类别 1 分公司  2 子公司  3 部门 (展示用)
     */
    private Integer companyType;

    /**
     * 单号前缀
     */
    private String billPrefix;

    /**
     * 县
     */
    private String county;

    /**
     * 用户中心id
     */
    private String ucenterId;

    /**
     * 承运商通用邀请
     */
    private String inviteCarrierLink;

    /**
     * 收货方通用邀请
     */
    private String inviteConsigneeLink;

    /**
     * 司机通用邀请
     */
    private String inviteDriverLink;

    /**
     * 企业公钥
     */
    private String publicKey;

    /**
     * 云计费子账号
     */
    private String subAccountCode;

    /**
     * 企业是否已激活
     */
    private Boolean isActive;

    /**
     * 查单套餐是否欠费：0欠费 1未欠费
     */
    private Integer tranComboIsOverdue;

    /**
     * saasId
     */
    private String saasId;
}