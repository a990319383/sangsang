package com.sangsang.test;

import com.sangsang.config.properties.FieldProperties;
import com.sangsang.config.properties.TransformationProperties;
import com.sangsang.domain.constants.SymbolConstant;
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
    //oracle的分页错误写法（内层是大于，外层是小于）
    //此种写法的执行结果一直都是空
    //oracle的ROWNUM是从1开始的，内层过滤条件拿到的第一条ROWNUM是1，不满足大于2的条件，再取下一条，ROWNUM还是1，一直不满足大于2，所以一直拿不到结果集
    String s0 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page WHERE ROWNUM > 2 \n" +
            ") WHERE row_id < 5";

    //oracle的分页写法1-1 ： 分页逻辑一部分写内层，一部分写嵌套的外层
    // 注意：这种写法的内层对于行号的判断只能是小于，如果是大于一个比1大的值，会永远都
    String s1 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page WHERE ROWNUM < 5 \n" +
            ") WHERE row_id > 2";

    //oracle的分页写法1-2： 分页逻辑一部分写内层，一部分写嵌套的外层
    // 内层分页条件判断时还存在其它的条件
    //注意：内层的判断条件是小于，外层的判断条件是大于。如果写反的话，这种分页语法是有问题的，会得到一个空的结果集
    //注意：此类语法转换后，哪怕一样的数据，oracle和mysql的执行结果可能不一致，因为缺少order by 条件，两者的排序规则不同，得到的结果集可能是不同的
    String s2 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page WHERE ROWNUM < 5  and phone != 'xxx' \n" +
            ") WHERE row_id > 2";

    //oracle的分页写法2-1 ： 分页逻辑全部写外层
    String s3 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page\n" +
            ") WHERE row_id > ? AND row_id <= ?";

    //oracle的分页写法2-2 ： 分页逻辑全部写外层
    // 内层存在其它条件
    String s4 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER  \n" +
            "    ) tmp_page where phone != 'xxx' \n" +
            ") WHERE row_id > 2 AND row_id <= 5";

    //oracle的分页写法3-1 ： 分页逻辑全部写外层，且使用between
    //注意：BETWEEN是左右都是闭区间，对比写法1，写法2
    String s5 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page\n" +
            ") WHERE row_id BETWEEN ? AND ?";

    //oracle的分页写法3-2 ： 分页逻辑全部写外层，且使用between
    //注意：BETWEEN是左右都是闭区间，对比写法1，写法2
    String s6 = "SELECT * FROM (\n" +
            "    SELECT tmp_page.*, ROWNUM row_id FROM (\n" +
            "        SELECT * FROM TB_USER \n" +
            "    ) tmp_page where phone != 'xxx' \n" +
            ") WHERE row_id BETWEEN ? AND ?";

    //orcale的分页写法4-1: 高版本支持
    String s7 = "SELECT * FROM TB_USER \n" +
            "OFFSET 7 ROWS FETCH NEXT 10 ROWS ONLY";

    //oracle的分页写法4-2: 缺了offset
    String s8 = "SELECT * FROM TB_USER \n" +
            "FETCH NEXT 10 ROWS ONLY";

    //oracle的分页写法4-3: 缺了 fetch
    String s9 = "SELECT * FROM TB_USER \n" +
            "OFFSET 2 ROWS ";

    //分页在内层，去掉行号字段后会移除嵌套的子查询,测试其它功能是否正确
    String s10 = "SELECT \n" +
            "  a.* \n" +
            "FROM \n" +
            "(SELECT * FROM (\n" +
            "SELECT TB.*, ROW_NUMBER() OVER (ORDER BY id) AS row_num \n" +
            "FROM TB_USER TB\n" +
            ") t \n" +
            "WHERE row_num BETWEEN 2 AND 5)a ";


    //窗口函数测试用例1：无 PARTITION BY  整体无order by
    String s11 = "SELECT * FROM (\n" +
            "    SELECT TB.*, ROW_NUMBER() OVER (ORDER BY create_time) AS row_num \n" +
            "    FROM TB_USER TB\n" +
            ") t " +
            "WHERE row_num BETWEEN 2 AND 5";

    //窗口函数测试用例2：有 PARTITION BY 整体无order by
    String s12 = "SELECT * FROM (\n" +
            "    SELECT TB.*, ROW_NUMBER() OVER (PARTITION BY phone ORDER BY create_time) AS row_num \n" +
            "    FROM TB_USER TB\n" +
            ") t \n" +
            "WHERE row_num BETWEEN 2 AND 5";

    //窗口函数测试用例3： 无 PARTITION BY  整体有order by
    String s13 = "SELECT * FROM (\n" +
            "    SELECT TB.*, ROW_NUMBER() OVER (ORDER BY create_time) AS row_num \n" +
            "    FROM TB_USER TB\n" +
            ") t \n" +
            "WHERE row_num BETWEEN 2 AND 5  order by id asc";

    //窗口函数测试用例4：有 PARTITION BY 整体有order by
    String s14 = "SELECT * FROM (\n" +
            "    SELECT TB.*, ROW_NUMBER() OVER (PARTITION BY phone ORDER BY create_time) AS row_num \n" +
            "    FROM TB_USER TB\n" +
            ") t \n" +
            "WHERE row_num BETWEEN 2 AND 5 order by id asc";


    //子查询必须拥有别名 （oracle可以没别名，但是mysql必须有）
    String s15 = "SELECT * from (SELECT * FROM tb_user) where id = 1";


    //Oracle NVL
    String s16 = "SELECT NVL(phone, '13800000000') FROM TB_USER";
    String s17 = "SELECT NVL(phone, NVL(login_name, 'guest')) FROM TB_USER";

    //Oracle SUBSTR / INSTR / LENGTH
    String s18 = "SELECT SUBSTR(phone, 2, 3) FROM TB_USER";
    String s19 = "SELECT SUBSTR(login_name, 2) FROM TB_USER";
    String s20 = "SELECT INSTR(login_name, 'a') FROM TB_USER";
    String s21 = "SELECT INSTR(login_name, 'a', 2) FROM TB_USER";
    String s22 = "SELECT LENGTH(phone), LENGTH(login_name) FROM TB_USER";

    //Oracle SYSDATE
    String s23 = "SELECT SYSDATE FROM TB_USER";
    String s24 = "SELECT SYSDATE() FROM TB_USER";

    //Oracle TO_CHAR / TO_DATE
    String s25 = "SELECT TO_CHAR(create_time, 'YYYY-MM-DD') FROM TB_USER";
    String s26 = "SELECT TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI:SS') FROM TB_USER";
    String s27 = "SELECT TO_DATE('2026-03-24', 'YYYY-MM-DD') FROM TB_USER";
    String s28 = "SELECT TO_DATE('2026-03-24 12:34:56', 'YYYY-MM-DD HH24:MI:SS') FROM TB_USER";

    //Oracle DECODE / TO_NUMBER
    String s29 = "SELECT DECODE(role_id, 1, 'admin', 'other') FROM TB_USER";
    String s30 = "SELECT DECODE(role_id, 1, 'admin', 2, 'user', 'other') FROM TB_USER";
    String s31 = "SELECT DECODE(role_id, 1, 'admin', 2, 'user') FROM TB_USER";
    String s32 = "SELECT TO_NUMBER(role_id) FROM TB_USER";
    String s33 = "SELECT TO_NUMBER('12.34', '999.99') FROM TB_USER";

    //多种 Oracle 语法组合使用
    String s34 = "SELECT NVL(TO_CHAR(create_time, 'YYYY-MM-DD'), '2026-03-24'), " +
            "DECODE(role_id, 1, TO_NUMBER('1'), TO_NUMBER('2')) FROM TB_USER";

    //多层嵌套查询，包含常量字段，外层常量字段作为字段名查询
    String s35 = "SELECT b.\"un\", b.\"tmpName\", b.\"en\" FROM (SELECT a.\"un\", a.\"tmpName\", \"tmpName\" AS en FROM (SELECT \"USER_NAME\" AS un, '中文' AS tmpName FROM TB_USER) a) b";

    //表和字段都用""引起来了
    String s36 = "SELECT \"USER_NAME\" FROM \"TB_USER\"  ";

    /**
     * oracle转mysql语法转换器测试
     *
     * @author liutangqi
     * @date 2025/5/22 11:01
     * @Param []
     **/
    @Test
    public void oracle2mysqlTransformation() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //设置模式是oracle转mysql
        TransformationProperties transformation = fieldProperties.getTransformation();
        transformation.setPatternType(TransformationPatternTypeConstant.ORACLE_2_MYSQL);
//        fieldProperties.setIdentifierQuote(Arrays.asList(SymbolConstant.FLOAT, SymbolConstant.SINGLE_QUOTES));
        fieldProperties.setIdentifierQuote(Arrays.asList(SymbolConstant.FLOAT, SymbolConstant.DOUBLE_QUOTES));
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        //需要的sql
        String sql = s35;

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
        for (int i = 0; i < sqls.size(); i++) {
            System.out.println((i + 1) + " : " + sqls.get(i));
        }
    }


//----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------

    //需要测试的sql
    List<String> sqls = Arrays.asList(
            s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15,
            s16, s17, s18, s19, s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31, s32, s33, s34, s35, s36
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
    public void tfCheck() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //设置模式是oracle转mysql
        TransformationProperties transformation = fieldProperties.getTransformation();
        transformation.setPatternType(TransformationPatternTypeConstant.ORACLE_2_MYSQL);
        fieldProperties.setIdentifierQuote(Arrays.asList(SymbolConstant.FLOAT, SymbolConstant.DOUBLE_QUOTES));
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);


        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //语法转换时，对当前sql的进行占位符替换，并且mocksql入参值
            sql = CacheTestHelper.tfHolderMock(sql);

            //开始解析sql
            Statement statement = JsqlparserUtil.parse(sql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();

            //原始sql的占位符还原
            sql = StringUtils.placeholder2Question(sql);

            //找答案
            String answer = AnswerUtil.readTfAnswerToFile(this, TransformationPatternTypeConstant.ORACLE_2_MYSQL, sql);
            String sqlFieldName = ReflectUtils.getFieldNameByValue(this, sql);
            if (StringUtils.isBlank(answer)) {
                System.out.println("这个sql没答案，自己检查，然后把正确答案给录到com.sangsang.answer.standard." + TransformationPatternTypeConstant.ORACLE_2_MYSQL + "下面 :" + sqlFieldName);
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
     *
     * @author liutangqi
     * @date 2025/6/6 15:31
     * @Param []
     **/
    @Test
    public void transformationAnswerWrite() throws Exception {
        //设置测试配置
        FieldProperties fieldProperties = CacheTestHelper.buildTestProperties();
        //设置模式是oracle转mysql
        TransformationProperties transformation = fieldProperties.getTransformation();
        transformation.setPatternType(TransformationPatternTypeConstant.ORACLE_2_MYSQL);
        fieldProperties.setIdentifierQuote(Arrays.asList(SymbolConstant.FLOAT, SymbolConstant.DOUBLE_QUOTES));
        //初始化缓存
        CacheTestHelper.testInit(fieldProperties);

        for (String sql : sqls) {
            //语法转换时，对当前sql的进行占位符替换，并且mocksql入参值
            String testSql = CacheTestHelper.tfHolderMock(sql);
            //开始解析sql
            //开始进行语法转换
            Statement statement = JsqlparserUtil.parse(testSql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();
            //原始sql的占位符还原
            sql = StringUtils.placeholder2Question(sql);
            AnswerUtil.writeTfAnswerToFile(this, TransformationPatternTypeConstant.ORACLE_2_MYSQL, sql, resultSql);
        }
    }
}
