package com.sangsang.test;

import com.sangsang.config.properties.SangSangProperties;
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
public class TfMysql2DMTest {


    //DATE_SUB 函数转换
    String s1 = "SELECT DATE_SUB(DATE_FORMAT(NOW(),'%Y-%m-%d'),INTERVAL 30 DAY) AS f1, DATE_SUB(DATE_FORMAT(CREATE_TIME ,'%Y-%m-%d %H:%i:%s'),INTERVAL 30 DAY) AS f2, DATEADD(DAY, -30, DATE_FORMAT(CREATE_TIME, \"%Y-%m-%d\")) AS f3, `USER_NAME` AS f4 FROM `TB_USER`";

    //Date()函数
    String s2 = "select date(now()) AS f1, date(CREATE_TIME) FROM TB_USER";

    // where 条件后面的字符串是双引号
    String s3 = "SELECT USER_NAME , `PHONE` FROM TB_USER WHERE USER_NAME = \"西瓜皮\"";

    //like
    String s4 = "SELECT * FROM TB_USER WHERE `USER_NAME` LIKE \"瓜\" AND `USER_NAME` LIKE CONCAT(\"%\",\"西\",\"%\")";

    // 常量和字段嵌套查询
    String s5 = "select a.`un`, a.`tmpName`, `tmpName` as en from (select `USER_NAME` as un, '中文' as tmpName from `TB_USER` )a";

    //多层嵌套查询，包含常量字段
    String s6 = "select b.`un`, b.`tmpName`, b.`en` from (select a.`un`, a.`tmpName`, `tmpName` as en from (select `USER_NAME` as un, \"中文\" as tmpName from TB_USER )a)b";

    // union
    String s7 = "SELECT DATE_FORMAT(now(), \"%Y-%m-%d\") USER_NAME, `PHONE` FROM TB_USER WHERE USER_NAME != \"西瓜皮\" UNION ALL SELECT USER_NAME, `PHONE` FROM TB_USER WHERE USER_NAME != \"西瓜皮\"";

    // case when
    String s8 = "SELECT CASE `USER_NAME` WHEN \"西瓜\" THEN 1 WHEN \"南瓜\" THEN 2 ELSE `PHONE` END AS F1, CASE WHEN `USER_NAME` = \"西瓜2\" THEN 1 WHEN `USER_NAME` = \"南瓜2\" THEN 2 ELSE `PHONE` END AS F2, CASE WHEN concat(`USER_NAME`,\"瓜\") = \"西瓜\" THEN DATE_FORMAT(`CREATE_TIME` , \"%Y-%m-%d\") WHEN `USER_NAME` = \"南瓜\" THEN `PHONE` ELSE 3 END AS F3 FROM TB_USER";

    //exist
    String s9 = "select * from `tb_user` tu where exists ( select count(1) from `tb_user` tu where tu.`phone` = \"14874542121\") and tu.`user_name` like \"%xxx%\"";

    // md5
    String s10 = "select md5(`user_name`) from tb_user";

    //date_add
    String s11 = "select date_add(`create_time` ,INTERVAL 7 DAY) from tb_user";

    //group_concat
    String s12 = "select group_concat(user_name,phone) as f1, group_concat(user_name order by id desc) as f2 , group_concat(user_name separator ';') as f3, group_concat(user_name,'-',phone order by id desc separator ';') as f4 from tb_user";

    //LISTAGG
    String s13 = "select LISTAGG(USER_NAME , ',') WITHIN GROUP (ORDER BY id DESC) AS f1, LISTAGG(USER_NAME , ',') AS f2 FROM TB_USER";
    // 多字段in
    String s14 = "select * from tb_user tu where (`user_name`,`phone`)in ((\"冬瓜\",\"18555555555\"))";

    //convert 这个函数没有对标的转换函数，仅做警告日志输出
    String s15 = "SELECT convert(tu.phone using utf8mb4) from tb_user tu";

    // in (select)
    String s16 = "select * from tb_user where `phone` in (select `phone` from tb_user where `phone` like concat(\"%\",\"xxx\",\"%\"))";

    // 多字段 (f1,f2) in (select)
    String s17 = "select * from tb_user where (`phone`,`user_name`) in (select `phone`,`user_name` from tb_user where `phone` like concat(\"%\",\"xxx\",\"%\"))";

    //多字段 in
    String s18 = "select * from tb_user where (`phone`,`user_name`) in ((\"18555555555\",\"西\"),(\"18777777777\",\"瓜\"))";
    //函数(列) in
    String s19 = "select * from tb_user where concat(`phone`,\"^\",`user_name`) in (\"18555555555^西\",\"18777777777^瓜\")";

    // STR_TO_DATE
    String s20 = "select STR_TO_DATE(create_time, '%Y-%m-%d %H:%i:%s') from tb_user";

    //子查询没有别名
    String s21 = "SELECT * FROM( SELECT TMP.*, ROWNUM ROW_ID FROM ( SELECT * FROM tb_user a WHERE a.create_time > \"2021-01-02 15:12:01\" ORDER BY a.`create_time` DESC) TMP WHERE ROWNUM <= 1) WHERE ROW_ID > 10";

    // SUBSTRING_INDEX
    String s22 = "SELECT SUBSTRING_INDEX(\"www.example.com\", \".\", 2)";

    // SUBSTRING_INDEX + GROUP_CONCAT
    String s23 = "select SUBSTRING_INDEX(GROUP_CONCAT( `user_name`),\",\",2) from tb_user";

    //DATEDIFF
    String s24 = "SELECT DATEDIFF('2025-06-23','2025-07-25')";

    //BETWEEN
    String s25 = "select * from tb_user where role_id BETWEEN 1 and 100";

    //group by
    String s26 = "select * from tb_user where role_id BETWEEN 1 and 100 group by id,user_name";

    //select (select) from
    String s27 = "select tu.`phone` as ph , `menu_name` as mName, (select tm2.`menu_name` from `tb_menu` tm2 where tm2.id = tm.id and tm2.id = ? and tu.phone = ? ) as m2Name from `tb_user` tu left join `tb_menu` tm on tu.id = tm.`id`";

    //带有case when
    String u1 = "update tb_user set `phone` = (CASE `phone` WHEN \"1\" THEN \"18111111111\" ELSE `phone` end ) where `id` = 5";

    //where 里面有函数
    String d1 = "DELETE FROM tb_user WHERE `phone` LIKE concat(\"%\",`user_name`,\"%\")";

    //同时插入多条数据
    String i1 = "insert into tb_user(`id`,`user_name`,`phone`) values(7,\"名字1\",\"18777777777\"),(9,\"名字2\",\"18999999999\")";

    //insert select
    String i2 = "insert into tb_user(`user_name`,`phone`) select `user_name`,`phone` from tb_user where `id` > 5";

    //占位符
    String i3 = "INSERT INTO xxl_job_log ( `job_group`, `job_id`, `trigger_time`, `trigger_code`, `handle_code` ) VALUES ( ?, ?, ?, ?, ? );";

    // value ===> values  (jsqlparser语法解析的时候会自动把value 转换为values)
    String i4 = "INSERT INTO tb_user (id,USER_NAME,PHONE) value (7,'名字3',18777777777),(9,'名字5',18999999999)";

    //测试的sql
    String testSql = "SELECT count(1) as transportSum, /* 运单总数数量 */ sum(IF(status = 0, 1, 0)) as orderSum, /* 已接单数量 */ sum(IF(status = 1, 1, 0)) as inGoodsSum, /* 装货中数量 */ sum(IF(status = 2, 1, 0)) as transportationSum, /* 运输中数量 */ sum(IF(status = 3, 1, 0)) as deliveredSum /* 已交货数量 */ FROM tms_transport WHERE status in (0,1,2,3) and del_flag = 0 AND create_date >= \"2021-01-01\" AND \"2026-01-01\" >= create_date AND org_id = 11111";

    /**
     * mysql转换为达梦的语法转换器测试
     *
     * @author liutangqi
     * @date 2025/5/22 11:01
     * @Param []
     **/
    @Test
    public void mysql2dmTransformation() throws Exception {
        //设置测试配置
        SangSangProperties sangSangProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(sangSangProperties);

        //需要的sql
        String sql = s5;
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
    List<String> sqls = Arrays.asList(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14,//s15,
            s16, s17, s18, s19, s20, s21, s22, s23, s24, s25, s26, s27,
            i1, i2, i3, i4,
            d1,
            u1
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
        SangSangProperties sangSangProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(sangSangProperties);

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            //开始解析sql
            Statement statement = JsqlparserUtil.parse(sql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();

            //找答案
            String answer = AnswerUtil.readTfAnswerToFile(this, TransformationPatternTypeConstant.MYSQL_2_DM, sql);
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
     *
     * @author liutangqi
     * @date 2025/6/6 15:31
     * @Param []
     **/
    @Test
    public void transformationAnswerWrite() throws Exception {
        //设置测试配置
        SangSangProperties sangSangProperties = CacheTestHelper.buildTestProperties();
        //初始化缓存
        CacheTestHelper.testInit(sangSangProperties);

        for (String sql : sqls) {
            //开始解析sql
            //开始进行语法转换
            Statement statement = JsqlparserUtil.parse(sql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();
            AnswerUtil.writeTfAnswerToFile(this, TransformationPatternTypeConstant.MYSQL_2_DM, sql, resultSql);
        }
    }
}
