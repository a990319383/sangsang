package com.sangsang.test;

import cn.hutool.json.JSONUtil;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.*;
import com.sangsang.visitor.isolation.IsolationStatementVisitor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import java.util.*;


/**
 * @author liutangqi
 * @date 2025/5/27 10:46
 */
public class IsolationTest {
    //普通查询，没有条件
    String s1 = "select * from tb_user tu";

    //带条件的查询，且条件里面有or
    String s2 = "select \n" +
            "*\n" +
            "from tb_user \n" +
            "WHERE phone like 'xxx'\n" +
            "and user_name = 'xxx'\n" +
            "or phone = 'xxx'";
    //多层嵌套查询
    String s3 = "select a.*,\n" +
            "(select tu.phone from tb_user tu) as ppp\n" +
            "from \n" +
            "(\n" +
            "select *\n" +
            "from tb_user \n" +
            "WHERE phone = 'xxx')a";
    //联表查询 + EXISTS（注意：EXISTS中拥有了外部上游的字段访问权限，当前处理是，内层也增加了外层的数据隔离）
    String s4 = "SELECT *\n" +
            "\t\tfrom tb_user t1 \n" +
            "\t\tleft join tb_user t2\n" +
            "\t\ton t1.id = t2.id \n" +
            "\t\tWHERE EXISTS (SELECT count(1) from tb_user )";

    // in select
    String s5 = "\tSELECT *\n" +
            "\tfrom tb_user tu \n" +
            "\tWHERE tu.id in (select id from tb_user)";

    //union all
    String s6 = "select * from tb_user where id = 2 union all select * from tb_user where id = 3";

    // select from (union all)
    String s7 = "\tselect \n" +
            "\t\t*\n" +
            "\t\tfrom (\n" +
            "\t\tselect * from tb_user where id = 2 union all select * from tb_user where id = 3\n" +
            "\t\t)a";

    //单表多策略，且单表不同策略之间关系是or
    String s8 = "select * from sys_user where loginName like 'xxx' or mobile = '18432154844'";

    /**
     * mysql转换为达梦的语法转换器测试
     *
     * @author liutangqi
     * @date 2025/5/22 11:01
     * @Param []
     **/
    @Test
    public void isolationTest() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        //需要的sql
        String sql = s8;

        //开始进行数据隔离
        Statement statement = JsqlparserUtil.parse(sql);
        IsolationStatementVisitor ilStatementVisitor = new IsolationStatementVisitor();
        statement.accept(ilStatementVisitor);

        System.out.println("----------------------原始sql-----------------------");
        System.out.println(sql);
        System.out.println("----------------------数据隔离后sql-----------------------");
        System.out.println(ilStatementVisitor.getResultSql());
        System.out.println("---------------------------------------------");
    }

    @Test
    public void otherTest() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        Map<String, Object> map = new FieldHashMapWrapper<>();
        map.put("1", 111);
        map.put("2", 222);
        map.put("3", 333);
        map.put("4", 444);
        map.put("5", 555);

        Iterator<Map.Entry<String,Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if ("2".equals(entry.getKey())) {
                iterator.remove();
            }
        }

        System.out.println( map);



    }


    //----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------
    //需要测试的sql
    List<String> sqls = Arrays.asList(
            s1, s2, s3, s4, s5, s6, s7, s8
    );


    /**
     * 校验语法转换处理是否正确
     * 哥们儿，来对答案了
     *
     * @author liutangqi
     * @date 2025/6/6 15:40
     * @Param []
     **/
    @Test
    public void isolationCheck() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //开始进行数据隔离
            Statement statement = JsqlparserUtil.parse(sql);
            IsolationStatementVisitor ilStatementVisitor = new IsolationStatementVisitor();
            statement.accept(ilStatementVisitor);
            String resultSql = ilStatementVisitor.getResultSql();

            //找答案
            String answer = AnswerUtil.readIsolationAnswerToFile(this, sql);
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
     * 将转换好的结果答案写入到文件中
     *
     * @author liutangqi
     * @date 2025/6/6 15:31
     * @Param []
     **/
    @Test
    public void isolationAnswerWrite() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);


        for (String sql : sqls) {
            //开始进行数据隔离
            Statement statement = JsqlparserUtil.parse(sql);
            IsolationStatementVisitor ilStatementVisitor = new IsolationStatementVisitor();
            statement.accept(ilStatementVisitor);
            AnswerUtil.writeIsolationAnswerToFile(this, sql, ilStatementVisitor.getResultSql());
        }
    }
}
