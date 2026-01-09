package com.sangsang.test;

import com.sangsang.config.properties.FieldProperties;
import com.sangsang.config.properties.TransformationProperties;
import com.sangsang.domain.constants.TransformationPatternTypeConstant;
import com.sangsang.util.AnswerUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.ReflectUtils;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.transformation.TransformationStatementVisitor;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author liutangqi
 * @date 2025/5/27 10:46
 */
public class TfOracle2MysqlTest {
    //oracle的分页写法1 ： 分页逻辑一部分写内层，一部分写嵌套的外层
    String s1 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page WHERE ROWNUM > ? \n" +
            ") WHERE row_id < ?";

    //oracle的分页写法2 ： 分页逻辑全部写外层
    String s2 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page\n" +
            ") WHERE row_id > 10 AND row_id <= 20";

    //oracle的分页写法3 ： 分页逻辑全部写外层，且使用between
    //注意：BETWEEN是左右都是闭区间，对比s1,s2
    String s3 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page\n" +
            ") WHERE row_id BETWEEN ? AND ?";

    //orcale的分页写法4: 高版本支持
    String s4 = "SELECT * FROM TB_USER \n" +
            "OFFSET 10 ROWS FETCH NEXT 10 ROWS ONLY";

    //分页写法5： 某些库是根据窗口函数来进行分页的，比如SqlServer，orcale也可以
    //如果要转换这种语法的话，还需要注意其中的排序字段，改造后的分页也需要按照这个排序方式进行改造
    String s5 = "SELECT * FROM (\n" +
            "    SELECT TB.*, ROW_NUMBER() OVER (ORDER BY id) AS row_num \n" +
            "    FROM TB_USER TB\n" +
            ") t \n" +
            "WHERE row_num BETWEEN 11 AND 20";

    //分页在内层，去掉行号字段后会移除嵌套的子查询,测试其它功能是否正确
    String s6 = "SELECT \n" +
            "  a.* \n" +
            "FROM \n" +
            "(SELECT * FROM (\n" +
            "SELECT TB.*, ROW_NUMBER() OVER (ORDER BY id) AS row_num \n" +
            "FROM TB_USER TB\n" +
            ") t \n" +
            "WHERE row_num BETWEEN 11 AND 20)a ";

    /**
     * oracle转mysql语法转换器测试
     *
     * @author liutangqi
     * @date 2025/5/22 11:01
     * @Param []
     **/
    @Test
    public void mysql2dmTransformation() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //设置模式是oracle转mysql
        TransformationProperties transformation = fieldProperties.getTransformation();
        transformation.setPatternType(TransformationPatternTypeConstant.ORACLE_2_MYSQL);
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        //需要的sql
        String sql = s2;

        //语法转换时，对当前sql的进行占位符替换，并且mocksql入参值
        sql = CacheTestHelper.tfHolderMock(sql);

        System.out.println("----------------------原始sql-----------------------");
        System.out.println(sql);

        //开始进行语法转换
        Statement statement = JsqlparserUtil.parse(sql);
        TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
        statement.accept(transformationStatementVisitor);

        System.out.println("----------------------语法转换后sql-----------------------");
        System.out.println(transformationStatementVisitor.getResultSql());
        System.out.println("---------------------------------------------");
    }

    @Test
    public void otherTest() {

    }


//----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------

    //需要测试的sql
    List<String> sqls = Arrays.asList(s1, s2, s3
    );


    /**
     * 校验语法转换处理是否正确
     * 哥们儿，来对答案了
     * todo-ltq
     *
     * @author liutangqi
     * @date 2025/6/6 15:40
     * @Param []
     **/
    @Test
    public void tfCheck() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //设置模式是oracle转mysql
        TransformationProperties transformation = fieldProperties.getTransformation();
        transformation.setPatternType(TransformationPatternTypeConstant.ORACLE_2_MYSQL);
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //开始解析sql
            Statement statement = JsqlparserUtil.parse(sql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();

            //找答案
            String answer = AnswerUtil.readTfAnswerToFile(this, sql);
            String sqlFieldName = ReflectUtils.getFieldNameByValue(this, sql);
            if (StringUtils.isBlank(answer)) {
                System.out.println("这个sql没答案，自己检查，然后把正确答案给录到com.sangsang.answer.standard下面 :" + sqlFieldName);
                System.out.println("原始sql: " + sql);
                return;
            }
            //注意：这里不能使用工具类里面的比较来判断结果是否正确，工具类里面的比较是把数据库标识符的引用符给全部去掉了判断相等的，语法转换功能涉及到这个符号的转换，所以只能使用忽略大小写的比较
            //if (StringUtils.sqlEquals(answer, resultSql)) {
            if (answer.equalsIgnoreCase(resultSql)) {
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
     * todo-ltq
     *
     * @author liutangqi
     * @date 2025/6/6 15:31
     * @Param []
     **/
    @Test
    public void transformationAnswerWrite() throws Exception {
        //设置测试配置
     /*   FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //设置模式是oracle转mysql
        TransformationProperties transformation = fieldProperties.getTransformation();
        transformation.setPatternType(TransformationPatternTypeConstant.ORACLE_2_MYSQL);
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (String sql : sqls) {
            //开始解析sql
            //开始进行语法转换
            Statement statement = JsqlparserUtil.parse(sql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();
            AnswerUtil.writeTfAnswerToFile(this, sql, resultSql);
        }*/
    }
}
