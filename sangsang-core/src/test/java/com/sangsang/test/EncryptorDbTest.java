package com.sangsang.test;

import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.util.*;
import com.sangsang.visitor.dbencrtptor.DBDencryptStatementVisitor;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * @author liutangqi
 * @date 2024/4/2 15:22
 */
//@ContextConfiguration(classes = {TableCache.class})
public class EncryptorDbTest {
    //------------select 解密 测试语句--------------

    //嵌套查询 ，带* ，where 条件带() 带or
    String s1 = "select tu.*, tm.menu_name from tb_user tu left join tb_menu tm on tu.id = tm.id where tu.phone like ? and tu.phone = ? and tm.menu_name != null and tm.parent_id in (?, ?, ?) or tm.path is not null or (tm.parent_id = ? and tm.create_time is not null )";

    //多层嵌套 带*
    String s2 = "select * from ( select * from tb_user )a where a.id >0 and a.phone = ?";


    //多层嵌套，where 条件中用上一层的字段作为筛选
    String s3 = "select b.ph, b.create_time as btime from ( select menuName, login_name, ph, a.create_time from ( select phone as ph, tu.login_name, tm.create_time, tm.menu_name as menuName from tb_user tu left join tb_menu tm on tu.id = tm.id) a where ph = ? ) b";

    // select (select xxx from ) from
    String s4 = "select tu.phone as ph , menu_name as mName, (select tm2.menu_name from tb_menu tm2 where tm2.id = tm.id and tm2.id = ? and tu.phone = ? ) as m2Name from tb_user tu left join tb_menu tm on tu.id = tm.id";

    //select (select xxx from ) + 嵌套
    String s5 = "select a.* from ( select tu.phone as ph, menu_name as mName, ( select tm2.menu_name from tb_menu tm2 where tm2.id = tm.id) as m2Name, ( select tu.phone from tb_menu tm3 where tm3.id = tu.id and tu.phone = ? ) as m3Ph from tb_user tu left join tb_menu tm on tu.id = tm.id) a";


    // union
    String s6 = "select * from tb_user tu where tu.phone = ? union all select * from tb_user tu2 where tu2.phone = ? union select * from tb_user tu3 where tu3.phone = ?";

    String s7 = "select b.ph, b.create_time as btime from ( select menuName, login_name, ph, a.create_time from ( select phone as ph, tu.login_name, tm.create_time, tm.menu_name as menuName from tb_user tu left join tb_menu tm on tu.id = tm.id) a where ph = ? ) b";

    // 同一张表一个字段出现多次，但别名不同  （tb_menu 表的 mmenu_name）
    String s8 = "select b.ph, b.create_time as btime from ( select menuName, login_name, ph, a.create_time from ( select phone as ph, tu.login_name, tm.menu_name as menuName, tm.* from tb_user tu left join tb_menu tm on tu.id = tm.id) a where ph = ? ) b";

    //case when
    String s9 = "select phone as ph, case tm.menu_name when '1' then phone else tu.user_name end as xxx, case tu.phone when ? then tu.phone else tm.menu_name end as yyy, case when tu.phone = ? then ? else tm.create_time end as zzz, tu.login_name, tm.menu_name as menuName, tm.* from tb_user tu left join tb_menu tm on tu.id = tm.id";


    // exists
    String s10 = "select * from tb_user tu where exists ( select count(1) from tb_menu tm where tm.id = tu.id and tu.phone = ? and tm.menu_name = tu.phone and tu.phone like '%xxx%' )";

    // select 查询字段 和where 带function  没别名
    String s11 = "select concat(?,tu.phone) from tb_user tu where concat(?,tu.phone) like ?";

    //select 查询字段 和where 带function  有别名
    String s12 = "select concat('xxx:',tu.phone) as ph from tb_user tu where concat(?,tu.phone) like ?";

    // select function 里面存在多个列
    String s13 = "select concat('xxx:', tu.phone , tm.id) as ph from tb_user tu left join tb_menu tm on tu.id = tm.id where concat('yyy:', tu.phone, tm.id) like '1840'";

    //select 嵌套查询，where 条件里面是function 处理后的别名
    String s14 = "select a.* from (select concat('xxx:', tu.phone , tm.id) as ph, tu.* from tb_user tu left join tb_menu tm on tu.id = tm.id where concat('yyy:', tu.phone, tm.id) like '1840')a where a.ph = 'tttt'";

    //select a.* from (select function )
    String s15 = "select a.* from ( select concat(tu.phone,'-',tu.create_time) as fff from tb_user tu )a";

    //where 中带case
    String s16 = "select * from tb_user tu left join tb_menu tm on tu.id = tm.id where tu.phone like ? and case tu.phone when ? then tu.phone like ? when 'xxx' then tm.id > ? end";

    // =  != 时，避免列运算，将Column 另外一边的进行加解密
    String s17 = "SELECT * from tb_user tu WHERE tu.phone = ? and 'xxx' = tu.phone and tu.phone = concat(? ,'yyy') and tu.phone != ? and tu.user_name = ?";

    // in 时，避免列运算，将Column 另外一边的进行加解密
    String s18 = "SELECT * from tb_user tu WHERE tu.phone not in (?,?)";

    // in (select xxx from) 子查询语法   字段和select的字段都是需要加密的
    String s19 = "select tu.* from tb_user tu where tu.phone in ( select t.phone from tb_user t where t.phone = ? )";

    // in (select xxx from) 子查询语法   字段需要加密 ，select的字段不需要加密的
    String s20 = "select tu.* from tb_user tu where tu.phone in ( select t.user_name from tb_user t where t.phone = ? )";

    // in (select xxx from) 子查询语法   字段不需要加密 ，select的字段需要加密的
    String s21 = "select tu.* from tb_user tu where tu.user_name in ( select t.phone from tb_user t where t.phone = ? )";

    // 多字段in   (xxx,yyy) in ( select xxx,yyy from )
    String s22 = "select * from tb_user tu left join tb_menu tm on tu.id = tm.id where (tu.phone,tm.id) in (select tu2.phone ,tu2.id from tb_user tu2 where tu2.phone = ? )";

    // in 前面不是 字段 右边是常量
    String s23 = "select * from tb_user tu where concat(\"aaa\",tu.phone) in (? , ?)";

    // in 前面不是字段  右边是子查询
    String s24 = "select * from tb_user tu where concat(tu.phone,'aaa') in ( select t.phone from tb_user t where t.phone = ? )";

    // 测试convert函数如何拼接的 (JsqlParse 4.4 不支持 convert函数！！！)
    String s25 = "SELECT convert(tu.phone using utf8mb4) from tb_user tu";

    //使用 cast 函数 某些场景下平替 convert 函数 （说的场景就是 AES_DECRYPT 中文解密乱码，点名批评一下）
    String s26 = "select cast(tu.phone as char) ,cast(tu.phone as char) as ppp from tb_user tu";

    //group by having
    String s27 = "SELECT tu.phone, count(1) as nums from tb_user tu group by tu.phone having count(1) > 1";

    //别名中有 ``
    String s28 = "SELECT `phone`,user_name from tb_user where `phone` like ? and phone = ?";

    // on 后面有写死的条件筛选
    String s29 = "select menuName, login_name, ph, a.create_time from ( select phone as ph, tu.login_name, tm.create_time, tm.menu_name as menuName from tb_user tu left join tb_menu tm on tu.id = tm.id and tu.login_name = ? and tu.phone = ?) a";


    // 正则 （select 和 where 条件都有 case when ）
    String s30 = "select tu.login_name , tu.user_name regexp ?, case when tu.phone regexp ? then tu.login_name regexp ? else tu.role_id regexp ? end from tb_user tu where phone REGEXP ?";

    //group_concat
    String s31 = "select group_concat(tu.phone,?) , group_concat(tu.phone) as ppp from tb_user tu left join tb_menu tm on tu.id = tm.id where tu.phone = ? group by tu.id";

    //json函数 (select 1.拼接成json  2.从json中根据key获取value值) todo-ltq
    String s32 = "";

    // 多字段in   (xxx,yyy) in ( (?,?),(?,?) )
    String s33 = "select * from tb_user tu left join tb_menu tm on tu.id = tm.id where (tu.phone,tm.id) in ((?,?),(?,?))";

    // 非Column in (select xxx from )
    String s34 = "select * from tb_user where ? in (select tu.phone from tb_user tu)";

    //非Column 多值in
    String s35 = "select * from tb_user where (?,?) in (select tu.phone ,tu.user_name from tb_user tu)";

    //join 子查询
    String s36 = "select * from tb_user tu left join( select * from tb_menu tm where tm.path = ?)tm_new on tu.phone = tm_new.id";

    // (xxx,yyy) in (select xxx,yyy ) 且上下游加解密算法不一致
    String s37 = "select * from tb_user tu where (tu.user_name,tu.phone,tu.phone) in (select tr.role_name ,tr.role_desc,tr.role_name from tb_role tr)";

    // (xxx) in (select xxx from)  且上下游加解密算法不一致
    String s38 = "select * from tb_user tu where (tu.phone) in (select tr.role_name from tb_role tr)";

    // xxx in (select xxx from)  且上下游加解密算法不一致 （注意：区别于s38，in的左边没括号）
    String s39 = "select * from tb_user tu where tu.phone in (select tr.role_name from tb_role tr)";

    // case when 后面的字段都属于数据库的表，且存在上下游算法不一致的情况
    String s40 = "select case tu.phone when tr.role_name then 1 when tr.role_desc then 2 when 'xxx' then 3 else 4 end , case when tu.phone = tr.role_name then 1 when tu.phone = tr.role_desc then 2 when 'xxx' = tr.role_name then 3 when 'yyy' = tr.role_desc then 4 else 5 end, tu.phone , tr.role_name , tr.role_desc from tb_user tu left join tb_role tr on tu.id = tr.id";

    //select from (union all)
    String s41 = "select * from (SELECT * FROM tb_user tu WHERE tu.phone = ? UNION ALL select * FROM tb_user tu2 WHERE tu2.phone = ? UNION SELECT * FROM tb_user tu3 WHERE tu3.phone = ?)a";

    //on 后面字段对应的算法不一致
    String s42 = "select * from tb_user tb left join tb_role tr on tb.phone = tr.role_name where tb.phone = ?";

    // in 后面字段存在嵌套子查询
    String s43 = "select * from tb_user tu where tu.phone in (select tmp.* from(select tr.role_name from tb_role tr)tmp)";

    //join 一张嵌套的表，且on的字段需要密文存储
    String s44 = "select * from tb_user tu join(select tmp.* from (select * from tb_role)tmp) a on tu.phone = a.role_name";

    //on 的时候存在嵌套 && 其中有字段时密文存储的 && 上游是密文  && 下游字段不是嵌套查询的结果
    String s45 = "select * from tb_user tu1 where tu1.phone in( select tr.role_name from tb_role tr join (select tmp.* from (select * from tb_user)tmp )a on tr.role_name = a.phone )";

    //on 的时候存在嵌套 && 其中有字段时密文存储的 && 上游是密文 && 下游字段是嵌套查询的结果
    String s46 = "select * from tb_user tu1 where tu1.phone in( select a.role_name from tb_user tu join( select tmp.* from ( select * from tb_role)tmp where tmp.role_name = ? )a on tu.phone = a.role_name)";

    // in 后面字段是查询的语句，并且查询的是一个常量 （这种语法本质是无意义的，只是完善程序结构）
    String s47 = "select * from tb_user tu where tu.phone in (select ? from tb_role tr where tr.role_name = ?)";

    //in 后面的字段是查询的嵌套查询的语句，并且查询的是一个常量（这种语法本质是无意义的，只是完善程序结构
    String s48 = "select * from tb_user tu where tu.phone in(select tmp.* from ( select ? from tb_role tr where tr.role_name = ?)tmp)";

    //in 子查询，下游子查询中会使用到上游的表字段  in的时候将上游的解析结果合并到下游
    String s49 = "select * from tb_user tu where tu.phone in (select tr.role_name from tb_role tr where tr.role_name = ? and tr.role_name = tu.phone)";

    //in的一个子查询，子查询中存在嵌套，内层嵌套使用到了in的上游的表字段(注意其中的tu.phone = ?的层级关系)
    //备注：这种语法性能有极大问题，且除mysql外，较多的数据库语法校验相对严格，是不支持这种写法的
    //简单测试了几种数据库： 支持这种语法的数据库（mysql 达梦）  不支持这种语法的数据库（clickhouse oracle）
    String s50 = "select * from tb_user tu where tu.phone in(select tmp.* from ( select ? from tb_role tr where tr.role_name = ? and tu.phone = ?)tmp)";

    //需要访问上层作用域时，上层的作用域是不是第一层
    String s51 = "SELECT a.* FROM(SELECT * FROM tb_user tu WHERE tu.phone IN (SELECT role_name FROM tb_role tr))a JOIN tb_menu tm ON a.id = tm.id";

    //临时测试sql
    String s_test_tmp = "select phone ph ,id as iii,'?' from tb_user where phone = ?";

    // -----------------insert 测试语句---------------------
    String i1 = "insert into tb_user(id, user_name ,phone) values(1,?,'18243512315'),(2,'南瓜',?)";

    // insert select 语句
    String i2 = "insert into tb_user(user_name,phone) ( select user_name,phone from tb_user tu where tu.phone = ? )";
    // ON DUPLICATE KEY UPDATE
    String i3 = "insert into tb_user (user_name,login_name,phone) values (?,?,?) ON DUPLICATE KEY UPDATE user_name = values(user_name), login_name = values(login_name), phone = values(phone), update_time = now()";

    // insert 语句没有指定字段  最新版本改动： 当开启了自动通过dataSource读取表结构信息后，我们是能读取到字段顺序的，这种情况下这种语法能兼容
    //备注：业务开发最好不要这样写，一旦表结构变更，字段数不对了，这个sql就报错了
    String i4 = "insert into tb_user values( ?,?,?,?,?,?,?,?)";

    //insert select ,其中insert语句和select语句中对应的字段，一个密文存储，一个明文存储，也有两个都是密文存储的
    String i5 = "insert into tb_user(user_name,phone,login_name) (select phone, phone,(select tu2.phone from tb_user tu2 where tu2.id = tu.id) from tb_user tu where tu.phone is not null )";

    //insert 单个值
    String i6 = "insert into tb_user(id, user_name ,phone) values(?,?,?)";

    //insert into 表 缺字段，并且子查询是select *   插入表没密文，select表有密文
    String i7 = "insert into tb_user_bak(select * from tb_user)";

    //insert into 表 缺字段，并且子查询是select *   插入表有密文，select表没密文
    String i8 = "insert into tb_user(select * from tb_user_bak)";

    //insert into 表 缺字段，并且子查询是select 别名.*  插入表没密文，select表有密文
    String i9 = "insert into tb_user_bak(select t.* from tb_user t)";

    //insert into 表 缺字段，并且子查询是select 别名.*  插入表有密文，select表没密文
    String i10 = "insert into tb_user(select t.* from tb_user_bak t)";

    // insert into 表 缺字段，并且子查询是select a.* from (select ) ，这里a是一个虚拟表，将*补齐的方式不同
    String i11 = "insert into tb_user_bak(select a.* from (select * from tb_user)a)";

    // insert into 表 缺字段，并且子查询是select a.* from (select ) ，这里a是一个虚拟表，将*补齐的方式不同 并且insert 和select的都是需要密文存储的
    String i12 = "insert into tb_user(select a.* from (select * from tb_user)a)";

    // --------------delete 测试语句 ---------------

    // delet join
    String d1 = "delete tu,tm from tb_user tu join tb_menu tm on tu.id = tm.id where tu.phone = ?";

    // delte 一张表
    String d2 = "delete from tb_user where phone like '%xxx%'";

    // --------------update 测试语句 ---------------

    //update 联表  set的时候存在 其它表的值，也存在常量值
    //左边不需要加密，右边需要
    //左右两边都需要加密
    //左边需要加密，右边不需要
    String u1 = "update tb_user tu join tb_menu tm on tu.id = tm.id set tm.menu_name = tm.`path` , tu.phone = tm.path , tu.phone = tm.menu_name , tu.phone = ? where tu.phone like ?";

    //update 联多张表  set的时候存在 其它表的值，也存在常量值
    String u2 = "update tb_user tu join tb_menu tm on tu.id = tm.id join tb_user tu2 on tu.id = tu2.id set tu.phone = tm.`path` , tm.menu_name = ? where tu.phone like ?";

    //update 一张表
    String u3 = "update tb_user set create_time = now(), phone = ? where phone = ?";

    String u4 = "UPDATE tb_user SET phone = ?, user_name = ?, login_pwd = ? WHERE id = ?";

    //联表update，且存在对应字段加解密算法不一致的情况
    String u5 = "update tb_role tr join tb_user tu on tr.id = tu.id set tr.role_name = tu.phone, tr.role_desc = tu.phone , tr.role_name = tu.user_name where tr.id = 1";


//----------------------------------------测试单条sql分割线---------------------------------------------------------

    /**
     * db模式下测试指定sql
     *
     * @author liutangqi
     * @date 2025/3/6 15:17
     * @Param []
     **/
    @Test
    public void testdbEncryptor() throws Exception {
        //设置测试配置
        SangSangProperties sangSangProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(sangSangProperties);

        //需要测试的sql
        String sql = s28;
        System.out.println("----------------------------------------------------------------------------");
        System.out.println(sql);
        System.out.println("----------------------------------------------------------------------------");

        //开始解析sql
        Statement statement = JsqlparserUtil.parse(sql);

        DBDencryptStatementVisitor DBDencryptStatementVisitor = new DBDencryptStatementVisitor();
        statement.accept(DBDencryptStatementVisitor);
        System.out.println("----------------------------------------------------------------------------");
        System.out.println((DBDencryptStatementVisitor.getResultSql()));
        System.out.println("----------------------------------------------------------------------------");

    }

//----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------

    //需要测试的sql
    List<String> sqls = Arrays.asList(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19,
            s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31, s33, s34, s35, s36, s37, s38, s39, s40, s41, s42, s43, s44, s45, s46, s47, s48, s49, s50, s51,
            i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12,
            d1, d2,
            u1, u2, u3, u4, u5
    );

    /**
     * 校验db模式下处理是否正确
     * 哥们儿，来对答案了
     *
     * @author liutangqi
     * @date 2025/3/6 15:02
     * @Param []
     **/
    @Test
    public void dbCheck() throws Exception {
        //设置测试配置
        SangSangProperties sangSangProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(sangSangProperties);

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //开始解析sql
            Statement statement = JsqlparserUtil.parse(sql);

            DBDencryptStatementVisitor DBDencryptStatementVisitor = new DBDencryptStatementVisitor();
            statement.accept(DBDencryptStatementVisitor);
            String resultSql = DBDencryptStatementVisitor.getResultSql();

            //找答案
            String answer = AnswerUtil.readDBAnswerToFile(this, sql);
            String sqlFieldName = ReflectUtils.getFieldNameByValue(this, sql);
            if (StringUtils.isBlank(answer)) {
                System.out.println("这个sql没答案，自己检查，然后把正确答案给录到com.sangsang.answer.standard下面 :" + sqlFieldName);
                System.out.println("原始sql: " + sql);
                return;
            }
            if (StringUtils.sqlEquals(answer, resultSql)) {
                System.out.println("成功: " + sqlFieldName);
            } else {
                System.out.println("错误: " + sqlFieldName);
                System.out.println("原始sql: " + sql);
                System.out.println("-------------------------------------------------------");
                System.out.println("正确答案： " + answer);
                System.out.println("-------------------------------------------------------");
                System.out.println("当前答案： " + resultSql);
                return;
            }
        }
    }


//----------------------------------------写入处理好的答案分割线---------------------------------------------------------
//-----------------标准答案存储路径：com.sangsang.answer.standard
//-----------------此处答案输出路径：com.sangsang.answer.current

    /**
     * 将db模式下处理好的结果答案写入到文件中
     *
     * @author liutangqi
     * @date 2025/3/6 13:18
     * @Param []
     **/
    @Test
    public void dbAnswerWrite() throws Exception {
        //设置测试配置
        SangSangProperties sangSangProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(sangSangProperties);

        for (String sql : sqls) {
            //开始解析sql
            Statement statement = JsqlparserUtil.parse(sql);

            DBDencryptStatementVisitor DBDencryptStatementVisitor = new DBDencryptStatementVisitor();
            statement.accept(DBDencryptStatementVisitor);
            String resultSql = DBDencryptStatementVisitor.getResultSql();
            AnswerUtil.writeDBAnswerToFile(this, sql, resultSql);
        }
    }


    @Test
    public void otherTest() throws Exception {

    }

}
