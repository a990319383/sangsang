package com.sangsang.test;

import com.sangsang.config.properties.FieldProperties;
import com.sangsang.util.AnswerUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.ReflectUtils;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.fielddefault.FieldDefaultStatementVisitor;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author liutangqi
 * @date 2025/7/17 16:59
 */
public class FieldDefaultTest {

    // -----------------insert 测试语句---------------------
    //普通的插入多条数据，并带有开启了强制覆盖的update_time字段
    String i1 = "insert into tb_user(id, user_name ,phone,update_time) \n" +
            "values(1,?,'18243512315','2021-01-01 00:00:00'),(2,'南瓜',?,'2021-01-01 00:00:00')";

    //普通的插入多条数据，并带有未开启了强制覆盖的create_time字段
    String i2 = "insert into tb_user(id, user_name ,phone,create_time) \n" +
            "values(1,?,'18243512315','2021-01-01 00:00:00'),(2,'南瓜',?,'2021-01-01 00:00:00')";

    // insert select 语句
    String i3 = "insert into \n" +
            "tb_user(user_name,phone)\n" +
            "(\n" +
            "select  user_name,phone from  tb_user  tu\n" +
            "where tu.phone = ? \n" +
            ")";

    //insert select * 语句 （不支持这种insert select *的）
    String i4 = "  insert into tb_user(id,user_name,login_name,login_pwd,phone,role_id,create_time,update_time) \n" +
            "  (select * from tb_user where phone = 'xxx')";

    // ON DUPLICATE KEY UPDATE  update中存在需要覆盖的值 insert没有
    String i5 = "insert into tb_user\n" +
            "(user_name,login_name,phone)\n" +
            "values (?,?,?)\n" +
            "ON DUPLICATE KEY UPDATE\n" +
            "user_name = values(user_name),\n" +
            "login_name = values(login_name),\n" +
            "phone = values(phone),\n" +
            "update_time = now()";

    //ON DUPLICATE KEY UPDATE  insert中存在需要覆盖的值 ,update中没有
    String i6 = "insert into tb_user\n" +
            "(user_name,login_name,phone ,update_time)\n" +
            "values (?,?,?,?)\n" +
            "ON DUPLICATE KEY UPDATE\n" +
            "user_name = values(user_name),\n" +
            "login_name = values(login_name),\n" +
            "phone = values(phone)";

    //insert 单个值
    String i7 = "insert into tb_user(id, user_name ,phone) values(?,?,?)";

    // --------------update 测试语句 ---------------

    //update 联表  set的时候存在 其它表的值，也存在常量值
    String u1 = "update tb_user tu \n" +
            "join tb_menu tm \n" +
            "on tu.id = tm.id \n" +
            "set tm.menu_name = tm.`path` ,\n" +
            "tu.phone = tm.path \n" +
            " , tu.phone = tm.menu_name \n" +
            " , tu.phone = ? ,tu.create_time = null ,tm.update_time = ? \n" +
            "where tu.phone like ? ";
    //update 联表，但是只修改了其中一张表的字段，其它表字段没改
    String u2 = "update tb_user tu \n" +
            "join tb_menu tm \n" +
            "on tu.id = tm.id \n" +
            "set tu.phone = tm.path ,\n" +
            " tu.create_time = null ,\n" +
            " tu.update_time =  ?\n" +
            "where tu.phone like ? ";

    //update 单表多个字段
    String u3 = "update tb_user  set phone = 'xxx',login_name='yyy',role_id=null where phone like 'zzz' ";


    @Test
    public void fieldDefaultTest() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        //需要的sql
        String sql = i1;

        //开始进行数据隔离
        Statement statement = JsqlparserUtil.parse(sql);
        FieldDefaultStatementVisitor fDeStatementVisitor = new FieldDefaultStatementVisitor();
        statement.accept(fDeStatementVisitor);

        System.out.println("----------------------原始sql-----------------------");
        System.out.println(sql);
        System.out.println("----------------------设置默认值后的sql-----------------------");
        System.out.println(fDeStatementVisitor.getResultSql());
        System.out.println("---------------------------------------------");
    }


    @Test
    public void otherTest() {

        Map<String, Object> map = new HashMap<>();
        map.put("aaa", "111");
        map.put("bbb", "222");
        map.remove("aaa");
        System.out.println(map);
        map.remove("ccc");
        System.out.println(map);
    }


    //----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------
    //需要测试的sql
    List<String> sqls = Arrays.asList(
            i1, i2, i3, i5, i6, i7,//i4,
            u1, u2, u3
    );


    /**
     * 校验设置默认值处理是否正确
     * 哥们儿，来对答案了
     *
     * @author liutangqi
     * @date 2025/6/6 15:40
     * @Param []
     **/
    @Test
    public void defaultCheck() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //开始字段默认值设置
            Statement statement = JsqlparserUtil.parse(sql);
            FieldDefaultStatementVisitor fDeStatementVisitor = new FieldDefaultStatementVisitor();
            statement.accept(fDeStatementVisitor);
            String resultSql = fDeStatementVisitor.getResultSql();

            //找答案
            String answer = AnswerUtil.readFieldDefaultAnswerToFile(this, sql);
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

    @Test
    public void defaultAnswerWrite() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (String sql : sqls) {
            Statement statement = JsqlparserUtil.parse(sql);
            FieldDefaultStatementVisitor fDeStatementVisitor = new FieldDefaultStatementVisitor();
            statement.accept(fDeStatementVisitor);
            AnswerUtil.writeFieldDefaultAnswerToFile(this, sql, fDeStatementVisitor.getResultSql());
        }
    }

}
