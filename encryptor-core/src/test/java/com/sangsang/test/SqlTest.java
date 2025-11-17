package com.sangsang.test;

import cn.hutool.json.JSONUtil;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.util.*;
import com.sangsang.visitor.dbencrtptor.DBDencryptStatementVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.pojoencrtptor.PoJoEncrtptorStatementVisitor;
import cn.hutool.core.lang.Pair;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * @author liutangqi
 * @date 2024/4/2 15:22
 */
//@ContextConfiguration(classes = {TableCache.class})
public class SqlTest {
    //------------select 解密 测试语句--------------

    //嵌套查询 ，带* ，where 条件带() 带or
    String s1 = "select \n" +
            "\t\ttu.*,\n" +
            "\t\ttm.menu_name\n" +
            "from\n" +
            "\t\ttb_user tu\n" +
            "left join tb_menu tm \n" +
            "\t\ton\n" +
            "\t\ttu.id = tm.id\n" +
            "where\n" +
            "\ttu.phone like ? " +
            " and tu.phone = ?" +
            "\tand tm.menu_name != null\n" +
            "\tand tm.parent_id in (?, ?, ?)\n" +
            "\tor tm.path is not null\n" +
            "\tor (tm.parent_id = ?\n" +
            "\t\tand tm.create_time is not null )";

    //多层嵌套 带*
    String s2 = "select \n" +
            "\t\t*\n" +
            "from\n" +
            "\t(\n" +
            "\tselect\n" +
            "\t\t*\n" +
            "\tfrom\n" +
            "\t\ttb_user \n" +
            "\t\t)a\n" +
            "where\n" +
            "\ta.id >0\n" +
            "\tand a.phone = ? ";


    //多层嵌套，where 条件中用上一层的字段作为筛选
    String s3 = "select\n" +
            "\tb.ph,\n" +
            "\tb.create_time as btime\n" +
            "from\n" +
            "\t(\n" +
            "\tselect\n" +
            "\t\tmenuName,\n" +
            "\t\tlogin_name,\n" +
            "\t\tph,\n" +
            "\t\ta.create_time\n" +
            "\tfrom\n" +
            "\t\t(\n" +
            "\t\tselect\n" +
            "\t\t\tphone as ph,\n" +
            "\t\t\ttu.login_name,\n" +
            "\t\t\ttm.create_time,\n" +
            "\t\t\ttm.menu_name as menuName\n" +
            "\t\tfrom\n" +
            "\t\t\ttb_user tu\n" +
            "\t\tleft join tb_menu tm \n" +
            "\t\t\ton\n" +
            "\t\t\ttu.id = tm.id) a\n" +
            "\twhere\n" +
            "\t\tph = ?\n" +
            "\t\t\t) b";

    // select (select xxx from ) from
    String s4 = "select\n" +
            "\ttu.phone as ph ,\n" +
            "\tmenu_name as mName,\n" +
            "\t(select tm2.menu_name from tb_menu tm2 where tm2.id = tm.id and tm2.id = ? and tu.phone = ? ) as m2Name\n" +
            "from\n" +
            "\ttb_user tu\n" +
            "left join tb_menu tm \n" +
            "on tu.id = tm.id";

    //select (select xxx from ) + 嵌套
    String s5 = "select\n" +
            "\ta.*\n" +
            "from \n" +
            "\t\t(\n" +
            "\tselect\n" +
            "\t\t tu.phone  as ph,\n" +
            "\t\tmenu_name as mName,\n" +
            "\t\t(\n" +
            "\t\tselect\n" +
            "\t\t\ttm2.menu_name\n" +
            "\t\tfrom\n" +
            "\t\t\ttb_menu tm2\n" +
            "\t\twhere\n" +
            "\t\t\ttm2.id = tm.id) as m2Name,\n" +
            "\t\t(\n" +
            "\t\tselect\n" +
            "\t\t\ttu.phone\n" +
            "\t\tfrom\n" +
            "\t\t\ttb_menu tm3\n" +
            "\t\twhere\n" +
            "\t\t\ttm3.id = tu.id\n" +
            "\t\t\tand tu.phone =  ?\n" +
            "\t\t) as m3Ph\n" +
            "\tfrom\n" +
            "\t\ttb_user tu\n" +
            "\tleft join tb_menu tm on\n" +
            "\t\ttu.id = tm.id) a";


    // union
    String s6 = "\t\tselect * from tb_user tu where tu.phone = ? \n" +
            "\t\tunion all \n" +
            "\t\tselect * from tb_user tu2  where tu2.phone = ? \n" +
            "\t\tunion \n" +
            "\t\tselect * from tb_user tu3 where tu3.phone = ?";

    String s7 = "select\n" +
            "\tb.ph,\n" +
            "\tb.create_time as btime\n" +
            "from\n" +
            "\t(\n" +
            "\tselect\n" +
            "\t\tmenuName,\n" +
            "\t\tlogin_name,\n" +
            "\t\tph,\n" +
            "\t\ta.create_time\n" +
            "\tfrom\n" +
            "\t\t(\n" +
            "\t\tselect\n" +
            "\t\t\tphone as ph,\n" +
            "\t\t\ttu.login_name,\n" +
            "\t\t\ttm.create_time,\n" +
            "\t\t\ttm.menu_name as menuName\n" +
            "\t\tfrom\n" +
            "\t\t\ttb_user tu\n" +
            "\t\tleft join tb_menu tm \n" +
            "\t\t\ton\n" +
            "\t\t\ttu.id = tm.id) a\n" +
            "\twhere\n" +
            "\t\tph = ? \n" +
            "\t\t\t) b";

    // 同一张表一个字段出现多次，但别名不同  （tb_menu 表的 mmenu_name）
    String s8 = "select\n" +
            "\tb.ph,\n" +
            "\tb.create_time as btime\n" +
            "from\n" +
            "\t(\n" +
            "\tselect\n" +
            "\t\tmenuName,\n" +
            "\t\tlogin_name,\n" +
            "\t\tph,\n" +
            "\t\ta.create_time\n" +
            "\tfrom\n" +
            "\t\t(\n" +
            "\t\tselect\n" +
            "\t\t\tphone as ph,\n" +
            "\t\t\ttu.login_name,\n" +
            "\t\t\ttm.menu_name as menuName,\n" +
            "\t\t\ttm.*\n" +
            "\t\tfrom\n" +
            "\t\t\ttb_user tu\n" +
            "\t\tleft join tb_menu tm \n" +
            "\t\t\ton\n" +
            "\t\t\ttu.id = tm.id) a\n" +
            "\twhere\n" +
            "\t\tph = ?\n" +
            "\t\t\t) b";

    //case when
    String s9 = "select\n" +
            "\tphone as ph,\n" +
            "\tcase tm.menu_name \n" +
            "\twhen '1' then \n" +
            "\tphone  \n" +
            "\telse \n" +
            "\ttu.user_name \n" +
            "\tend as xxx,\n" +
            "\tcase tu.phone \n" +
            "\twhen ? then \n" +
            "\ttu.phone \n" +
            "\telse \n" +
            "\ttm.menu_name \n" +
            "\tend as yyy,\n" +
            "\tcase  \n" +
            "\twhen tu.phone = ? then \n" +
            "\t ? \n" +
            "\telse \n" +
            "\ttm.create_time \n" +
            "\tend as zzz,\n" +
            "\ttu.login_name,\n" +
            "\ttm.menu_name as menuName,\n" +
            "\ttm.*\n" +
            "from\n" +
            "\ttb_user tu\n" +
            "left join tb_menu tm \n" +
            "\ton\n" +
            "\ttu.id = tm.id";


    // exists
    String s10 = "\tselect * from \n" +
            "\ttb_user tu \n" +
            "\twhere \n" +
            "\texists ( select count(1) from tb_menu tm where tm.id = tu.id and tu.phone = ? \n" +
            "\tand tm.menu_name  = tu.phone and tu.phone like '%xxx%' \n" +
            "\t)";

    // select 查询字段 和where 带function  没别名
    String s11 = "select \n" +
            "concat(?,tu.phone)\n" +
            "from tb_user tu \n" +
            "where concat(?,tu.phone) like ?";

    //select 查询字段 和where 带function  有别名
    String s12 = "select \n" +
            "concat('xxx:',tu.phone) as ph\n" +
            "from tb_user tu \n" +
            "where concat(?,tu.phone) like ?";

    // select function 里面存在多个列
    String s13 = "select\n" +
            "\tconcat('xxx:', tu.phone , tm.id) as ph\n" +
            "from\n" +
            "\ttb_user tu\n" +
            "left join tb_menu tm \n" +
            "on\n" +
            "\ttu.id = tm.id\n" +
            "where\n" +
            "\tconcat('yyy:', tu.phone, tm.id) like '1840'";

    //select 嵌套查询，where 条件里面是function 处理后的别名
    String s14 = "select \n" +
            "a.*\n" +
            "from \n" +
            "(select\n" +
            "\tconcat('xxx:', tu.phone , tm.id) as ph,\n" +
            "\ttu.*\n" +
            "from\n" +
            "\ttb_user tu\n" +
            "left join tb_menu tm \n" +
            "on\n" +
            "\ttu.id = tm.id\n" +
            "where\n" +
            "\tconcat('yyy:', tu.phone, tm.id) like '1840')a\n" +
            "where a.ph = 'tttt'";

    //select a.* from (select function )
    String s15 = "\tselect \n" +
            "\t\ta.* \n" +
            "\t\tfrom (\n" +
            "\t\tselect \n" +
            "\t\tconcat(tu.phone,'-',tu.create_time) as fff\n" +
            "\t    from tb_user tu )a";

    //where 中带case
    String s16 = "\tselect\n" +
            "\t*\n" +
            "from tb_user tu\n" +
            "left join tb_menu tm \n" +
            "on tu.id = tm.id\n" +
            "where tu.phone like ? \n" +
            "and\n" +
            "case tu.phone\n" +
            "when ? then tu.phone like ? \n" +
            "when 'xxx' then tm.id > ? \n" +
            "end";

    // =  != 时，避免列运算，将Column 另外一边的进行加解密
    String s17 = "SELECT * from tb_user tu \n" +
            "WHERE  tu.phone = ?\n" +
            "and 'xxx' = tu.phone \n" +
            "and tu.phone = concat(? ,'yyy')\n" +
            "and tu.phone != ?\n" +
            "and tu.user_name = ?";

    // in 时，避免列运算，将Column 另外一边的进行加解密
    String s18 = "SELECT *\n" +
            "from tb_user tu \n" +
            "WHERE tu.phone not in (?,?)";

    // in (select xxx from) 子查询语法   字段和select的字段都是需要加密的
    String s19 = "select \n" +
            "tu.*\n" +
            "from tb_user tu \n" +
            "where  tu.phone in (\n" +
            "select t.phone from tb_user t \n" +
            "where t.phone = ? " +
            ")";

    // in (select xxx from) 子查询语法   字段需要加密 ，select的字段不需要加密的
    String s20 = "select \n" +
            "tu.*\n" +
            "from tb_user tu \n" +
            "where  tu.phone in (\n" +
            "select t.user_name from tb_user t \n" +
            "where t.phone = ? " +
            ")";

    // in (select xxx from) 子查询语法   字段不需要加密 ，select的字段需要加密的
    String s21 = "select \n" +
            "tu.*\n" +
            "from tb_user tu \n" +
            "where  tu.user_name in (\n" +
            "select t.phone from tb_user t \n" +
            "where t.phone = ? " +
            ")";

    // 多字段in   (xxx,yyy) in ( select xxx,yyy from )
    String s22 = "select *\n" +
            "from tb_user tu \n" +
            "left join tb_menu tm \n" +
            "on tu.id =  tm.id \n" +
            "where (tu.phone,tm.id) in (select tu2.phone ,tu2.id from tb_user tu2 where tu2.phone = ? ) ";

    // in 前面不是 字段 右边是常量
    String s23 = "select * from tb_user tu \n" +
            "where  concat(\"aaa\",tu.phone) in (? , ?)";

    // in 前面不是字段  右边是子查询
    String s24 = "\n" +
            "select\n" +
            "\t*\n" +
            "from\n" +
            "\ttb_user tu\n" +
            "where\n" +
            "\tconcat(tu.phone,'aaa') in (\n" +
            "\tselect\n" +
            "\t\tt.phone\n" +
            "\tfrom\n" +
            "\t\ttb_user t\n" +
            "\twhere\n" +
            "\t\tt.phone = ? \n" +
            "            )";

    // 测试convert函数如何拼接的 (JsqlParse 4.4 不支持 convert函数！！！)
    String s25 = "SELECT \n" + "convert(tu.phone using utf8mb4)\n" + "from tb_user tu";

    //使用 cast 函数 某些场景下平替 convert 函数 （说的场景就是 AES_DECRYPT 中文解密乱码，点名批评一下）
    String s26 = "select cast(tu.phone as char) ,cast(tu.phone as char) as ppp from tb_user tu";

    //group by having
    String s27 = "SELECT \n" +
            "tu.phone,\n" +
            "count(1) as nums\n" +
            "from tb_user tu \n" +
            "group by tu.phone \n" +
            "having count(1) > 1";

    //别名中有 ``
    String s28 = "\tSELECT `phone`,user_name  from tb_user \n" +
            "\twhere `phone` like ? \n" +
            "\tand phone = ? ";

    // on 后面有写死的条件筛选
    String s29 = "select menuName, login_name, ph, a.create_time from ( select phone as ph, tu.login_name, tm.create_time, tm.menu_name as menuName from tb_user tu left join tb_menu tm on tu.id = tm.id and tu.login_name = ? and tu.phone = ?) a";


    // 正则 （select 和 where 条件都有 case when ）
    String s30 = " select \n" +
            " tu.login_name ,\n" +
            " tu.user_name regexp ?,\n" +
            " case \n" +
            " when tu.phone regexp ? \n" +
            " then tu.login_name regexp ? \n" +
            " else tu.role_id regexp ? \n" +
            " end\n" +
            "from tb_user tu \n" +
            "where phone REGEXP ?";

    //group_concat
    String s31 = "select \n" +
            "group_concat(tu.phone,?) ,\n" +
            "group_concat(tu.phone) as ppp \n" +
            "from tb_user tu \n" +
            "left join tb_menu tm \n" +
            "on tu.id = tm.id \n" +
            "where tu.phone = ?\n" +
            "group by tu.id ";

    //json函数 (select 1.拼接成json  2.从json中根据key获取value值) todo-ltq
    String s32 = "";

    // 多字段in   (xxx,yyy) in ( (?,?),(?,?) )
    String s33 = "select *\n" +
            "from tb_user tu \n" +
            "left join tb_menu tm \n" +
            "on tu.id =  tm.id \n" +
            "where (tu.phone,tm.id) in ((?,?),(?,?))";

    // 非Column in (select xxx from )
    String s34 = "select * from tb_user  where ? in (select tu.phone from tb_user tu) ";

    //非Column 多值in
    String s35 = "select * from tb_user  where (?,?) in (select tu.phone ,tu.user_name from tb_user tu)";

    //join 子查询
    String s36 = "select * from tb_user tu left join( select * from tb_menu tm where tm.path = ?)tm_new on tu.phone = tm_new.id";

    // (xxx,yyy) in (select xxx,yyy ) 且上下游加解密算法不一致
    String s37 = "select * from tb_user tu where (tu.user_name,tu.phone,tu.phone) in (select tr.role_name ,tr.role_desc,tr.role_name from tb_role tr)";

    // (xxx) in (select xxx from)  且上下游加解密算法不一致
    String s38 = "select * from tb_user tu where (tu.phone) in (select tr.role_name from tb_role tr)";

    // xxx in (select xxx from)  且上下游加解密算法不一致 （注意：区别于s38，in的左边没括号）
    String s39 = "select * from tb_user tu where tu.phone in (select tr.role_name from tb_role tr)";

    // case when 后面的字段都属于数据库的表，且存在上下游算法不一致的情况
    String s40 = "  select \n" +
            "  case tu.phone \n" +
            "  when tr.role_name then 1\n" +
            "  when tr.role_desc then 2\n" +
            "  when 'xxx' then 3\n" +
            "  else 4 \n" +
            "  end ,\n" +
            "  case\n" +
            "  when tu.phone = tr.role_name then 1\n" +
            "  when tu.phone = tr.role_desc then 2\n" +
            "  when 'xxx' = tr.role_name then 3\n" +
            "  when 'yyy' = tr.role_desc then 4\n" +
            "  else 5\n" +
            "  end,\n" +
            "  tu.phone ,\n" +
            "  tr.role_name ,\n" +
            "  tr.role_desc \n" +
            "  from tb_user tu \n" +
            "  left join tb_role tr \n" +
            "  on tu.id = tr.id ";

    //select from (union all)
    String s41 = "select * from (SELECT  *\n" +
            "FROM tb_user tu\n" +
            "WHERE tu.phone = ?\n" +
            "UNION ALL\n" +
            "select *\n" +
            "FROM  tb_user tu2\n" +
            "WHERE tu2.phone = ?\n" +
            "UNION\n" +
            "SELECT  *\n" +
            "FROM tb_user tu3\n" +
            "WHERE  tu3.phone = ?)a";

    //on 后面字段对应的算法不一致
    String s42 = "select * from tb_user tb left join tb_role tr on tb.phone = tr.role_name where tb.phone = ? ";

    // -----------------insert 测试语句---------------------
    String i1 = "insert into tb_user(id, user_name ,phone) \n" +
            "values(1,?,'18243512315'),(2,'南瓜',?)";

    // insert select 语句
    String i2 = "insert into \n" +
            "tb_user(user_name,phone)\n" +
            "(\n" +
            "select  user_name,phone from  tb_user  tu\n" +
            "where tu.phone = ? \n" +
            ")";
    // ON DUPLICATE KEY UPDATE
    String i3 = "insert into tb_user\n" +
            "(user_name,login_name,phone)\n" +
            "values (?,?,?)\n" +
            "ON DUPLICATE KEY UPDATE\n" +
            "user_name = values(user_name),\n" +
            "login_name = values(login_name),\n" +
            "phone = values(phone),\n" +
            "update_time = now()";

    // insert 语句没有指定字段  注意：不支持此语法！！！ 无法确定字段顺序
//    String i4 = "insert into tb_user  values( ?,?,?,?,?,?,?,?)";

    //insert select ,其中insert语句和select语句中对应的字段，一个密文存储，一个明文存储，也有两个都是密文存储的
    String i5 = "insert into  tb_user(user_name,phone,login_name)\n" +
            "(select  phone, phone,(select tu2.phone from tb_user tu2 where tu2.id = tu.id) from tb_user tu\n" +
            "where tu.phone is not null \n" +
            ")";

    //insert 单个值
    String i6 = "insert into tb_user(id, user_name ,phone) values(?,?,?)";

    // --------------delete 测试语句 ---------------

    // delet join
    String d1 = "delete tu,tm \n" +
            "from tb_user tu \n" +
            "join tb_menu tm \n" +
            "on tu.id = tm.id \n" +
            "where tu.phone = ? ";

    // delte 一张表
    String d2 = "\t delete from tb_user \n" +
            "\twhere phone like '%xxx%' ";

    // --------------update 测试语句 ---------------

    //update 联表  set的时候存在 其它表的值，也存在常量值
    String u1 = "update tb_user tu \n" +
            "join tb_menu tm \n" +
            "on tu.id = tm.id \n" +
            "set tm.menu_name = tm.`path` ,\n" + //左边不需要加密，右边需要
            "tu.phone = tm.path \n" +// 左右两边都需要加密
            " , tu.phone = tm.menu_name \n" + //左边需要加密，右边不需要
            " , tu.phone = ? \n" +
            "where tu.phone like ? ";

    //update 联多张表  set的时候存在 其它表的值，也存在常量值
    String u2 = "update tb_user tu \n" +
            "join tb_menu tm \n" +
            "on tu.id = tm.id \n" +
            "join tb_user tu2 \n" +
            "on tu.id = tu2.id \n" +
            "set tu.phone = tm.`path` ,\n" +
            "tm.menu_name = ? \n" +
            "where tu.phone like ? ";

    //update 一张表
    String u3 = "\tupdate tb_user \n" +
            "\tset create_time = now(),\n" +
            "\tphone = ?\n" +
            "\twhere phone = ?";

    String u4 = "UPDATE sys_user\n" +
            "        SET\n" +
            "        last_login_date = ?,\n" +
            "        update_by = ?,\n" +
            "        update_date = ?\n" +
            "        WHERE\n" +
            "        id = ?";

    //联表update，且存在对应字段加解密算法不一致的情况
    String u5 = "\t\tupdate tb_role tr \n" +
            "\t\tjoin tb_user tu \n" +
            "\t\ton tr.id = tu.id \n" +
            "\t\tset tr.role_name  = tu.phone,\n" +
            "\t\ttr.role_desc  = tu.phone ,\n" +
            "\t\ttr.role_name = tu.user_name \n" +
            "\t\twhere tr.id = 1";


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
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        //需要测试的sql
        String sql = s41;
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


    /**
     * pojo模式下测试指定sql
     *
     * @author liutangqi
     * @date 2025/3/6 15:17
     * @Param []
     **/
    @Test
    public void testPoJoEncryptor() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        //需要测试的sql
        String sql = s11;
        System.out.println("----------------------------------------------------------------------------");
        System.out.println(sql);
        System.out.println("----------------------------------------------------------------------------");

        //将原sql ？ 替换为自定义的占位符
        String placeholderSql = StringUtils.question2Placeholder(sql);

        //开始解析sql
        Statement statement = JsqlparserUtil.parse(placeholderSql);
        PoJoEncrtptorStatementVisitor poJoEncrtptorStatementVisitor = new PoJoEncrtptorStatementVisitor();
        statement.accept(poJoEncrtptorStatementVisitor);
        System.out.println(JSONUtil.toJsonStr(poJoEncrtptorStatementVisitor.getFieldEncryptorInfos()));
        System.out.println(JSONUtil.toJsonStr(poJoEncrtptorStatementVisitor.getPlaceholderColumnTableMap()));
    }


//----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------

    //需要测试的sql
    List<String> sqls = Arrays.asList(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19,
            s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31, s33, s34, s35, s36, s37, s38, s39, s40, s41, s42,
            i1, i2, i3, i5, i6,//i4,
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
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

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


    /**
     * 校验pojo模式下处理是否正确
     * 哥们儿，来对答案了
     *
     * @author liutangqi
     * @date 2025/3/6 15:02
     * @Param []
     **/
    @Test
    public void pojoCheck() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //将原sql ？ 替换为自定义的占位符
            String placeholderSql = StringUtils.question2Placeholder(sql);

            //开始解析sql
            Statement statement = JsqlparserUtil.parse(placeholderSql);
            PoJoEncrtptorStatementVisitor poJoEncrtptorStatementVisitor = new PoJoEncrtptorStatementVisitor();
            statement.accept(poJoEncrtptorStatementVisitor);
            List<FieldEncryptorInfoDto> fieldEncryptorInfos = poJoEncrtptorStatementVisitor.getFieldEncryptorInfos();
            Map<String, ColumnTableDto> placeholderColumnTableMap = poJoEncrtptorStatementVisitor.getPlaceholderColumnTableMap();

            //找答案
            Pair<String, String> answer = AnswerUtil.readPOJOAnswerToFile(this, sql);
            String sqlFieldName = ReflectUtils.getFieldNameByValue(this, sql);
            if (answer == null) {
                System.out.println("这个sql没答案，自己检查，然后把正确答案给录到com.sangsang.answer.standard下面 :" + sqlFieldName);
                System.out.println("原始sql: " + sql);
                return;
            }
            if (CollectionUtils.jsonArrayEquals(JSONUtil.parseArray(answer.getKey()), JSONUtil.parseArray(JSONUtil.toJsonStr(fieldEncryptorInfos)))
                    && CollectionUtils.jsonObjectEquals(JSONUtil.parseObj(answer.getValue()), JSONUtil.parseObj(JSONUtil.toJsonStr(placeholderColumnTableMap)))) {
                System.out.println("成功: " + sqlFieldName);
            } else {
                System.out.println("错误: " + sqlFieldName);
                System.out.println("原始sql: " + sql);
                System.out.println("-------------------------------------------------------");
                if (!Objects.equals(answer.getKey(), JSONUtil.toJsonStr(fieldEncryptorInfos))) {
                    System.out.println("正确答案list： " + answer.getKey());
                    System.out.println("当前答案list： " + JSONUtil.toJsonStr(fieldEncryptorInfos));
                    System.out.println("-------------------------------------------------------");
                }
                if (!Objects.equals(answer.getValue(), JSONUtil.toJsonStr(placeholderColumnTableMap))) {
                    System.out.println("正确答案Map： " + answer.getValue());
                    System.out.println("当前答案Map： " + JSONUtil.toJsonStr(placeholderColumnTableMap));
                }
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
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (String sql : sqls) {
            //开始解析sql
            Statement statement = JsqlparserUtil.parse(sql);

            DBDencryptStatementVisitor DBDencryptStatementVisitor = new DBDencryptStatementVisitor();
            statement.accept(DBDencryptStatementVisitor);
            String resultSql = DBDencryptStatementVisitor.getResultSql();
            AnswerUtil.writeDBAnswerToFile(this, sql, resultSql);
        }
    }

    /**
     * 将pojo模式下处理好的结果答案写入到文件中
     *
     * @author liutangqi
     * @date 2025/3/6 13:18
     * @Param []
     **/
    @Test
    public void pojoAnswerWrite() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (String sql : sqls) {
            //将原sql ？ 替换为自定义的占位符
            String placeholderSql = StringUtils.question2Placeholder(sql);

            //开始解析sql
            Statement statement = JsqlparserUtil.parse(placeholderSql);
            PoJoEncrtptorStatementVisitor poJoEncrtptorStatementVisitor = new PoJoEncrtptorStatementVisitor();
            statement.accept(poJoEncrtptorStatementVisitor);
            List<FieldEncryptorInfoDto> fieldEncryptorInfos = poJoEncrtptorStatementVisitor.getFieldEncryptorInfos();
            Map<String, ColumnTableDto> placeholderColumnTableMap = poJoEncrtptorStatementVisitor.getPlaceholderColumnTableMap();
            AnswerUtil.writePOJOAnswerToFile(this, sql, fieldEncryptorInfos, placeholderColumnTableMap);
        }
    }

    @Test
    public void testParse() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        String sql = "select tu.* from tb_user tu";
        Statement statement = JsqlparserUtil.parse(sql);
        FieldParseParseTableSelectVisitor fieldParseParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        ((PlainSelect) statement).accept(fieldParseParseTableSelectVisitor);
        System.out.println(fieldParseParseTableSelectVisitor);
    }

}
