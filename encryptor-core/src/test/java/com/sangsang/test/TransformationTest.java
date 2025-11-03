package com.sangsang.test;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.LRUCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.config.properties.TransformationProperties;
import com.sangsang.domain.constants.TransformationPatternTypeConstant;
import com.sangsang.util.AnswerUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.ReflectUtils;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.transformation.TransformationStatementVisitor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author liutangqi
 * @date 2025/5/27 10:46
 */
public class TransformationTest {


    //DATE_SUB 函数转换
    String s1 = "SELECT\n" +
            "\tDATE_SUB(DATE_FORMAT(NOW(),'%Y-%m-%d'),INTERVAL 30 DAY) AS f1,\n" +
            "\tDATE_SUB(DATE_FORMAT(CREATE_TIME ,'%Y-%m-%d %H:%i:%s'),INTERVAL 30 DAY) AS f2,\n" +
            "\tDATEADD(DAY, -30, DATE_FORMAT(CREATE_TIME, \"%Y-%m-%d\")) AS f3,\n" +
            "\t`USER_NAME` AS f4\n" +
            "FROM\n" +
            "\t`TB_USER`";

    //Date()函数
    String s2 = "\tselect \n" +
            "\tdate(now()) AS f1,\n" +
            "\tdate(CREATE_TIME)\n" +
            "\tFROM TB_USER ";

    // where 条件后面的字符串是双引号
    String s3 = "\t SELECT \n" +
            "\t USER_NAME ,\n" +
            "\t `PHONE`\n" +
            "\t FROM TB_USER \n" +
            "\t WHERE USER_NAME = \"西瓜皮\"";

    //like
    String s4 = "SELECT *\n" +
            "\t FROM TB_USER \n" +
            "\t WHERE `USER_NAME` LIKE \"瓜\"\n" +
            "\t AND `USER_NAME` LIKE CONCAT(\"%\",\"西\",\"%\")";

    // 常量和字段嵌套查询
    String s5 = "select\n" +
            "\ta.`un`,\n" +
            "\ta.`tmpName`,\n" +
            "\t`tmpName` as en\n" +
            "from \n" +
            "(select\n" +
            "\t`USER_NAME` as un,\n" +
            "\t'中文' as tmpName\n" +
            "from `TB_USER` )a";

    //多层嵌套查询，包含常量字段
    String s6 = "select \n" +
            "b.`un`,\n" +
            "b.`tmpName`,\n" +
            "b.`en`\n" +
            "from \n" +
            "(select\n" +
            "\ta.`un`,\n" +
            "\ta.`tmpName`,\n" +
            "\t`tmpName` as en\n" +
            "from \n" +
            "(select\n" +
            "\t`USER_NAME` as un,\n" +
            "\t\"中文\" as tmpName\n" +
            "from TB_USER )a)b";

    // union
    String s7 = "SELECT DATE_FORMAT(now(), \"%Y-%m-%d\") USER_NAME, `PHONE` FROM TB_USER WHERE USER_NAME != \"西瓜皮\"\n" +
            "UNION ALL \n" +
            "SELECT USER_NAME, `PHONE` FROM TB_USER WHERE USER_NAME != \"西瓜皮\"";

    // case when
    String s8 = "SELECT \n" +
            "\tCASE\n" +
            "\t\t`USER_NAME` \n" +
            "\t\tWHEN \"西瓜\" THEN 1\n" +
            "\t\tWHEN \"南瓜\" THEN 2\n" +
            "\t\tELSE `PHONE` \n" +
            "\tEND AS F1,\n" +
            "\tCASE \n" +
            "\t\tWHEN `USER_NAME` = \"西瓜2\" THEN 1\n" +
            "\t\tWHEN `USER_NAME` = \"南瓜2\" THEN 2\n" +
            "\t\tELSE `PHONE` \n" +
            "\tEND AS F2,\n" +
            "\tCASE \n" +
            "\t\tWHEN concat(`USER_NAME`,\"瓜\") = \"西瓜\" THEN DATE_FORMAT(`CREATE_TIME` , \"%Y-%m-%d\")\n" +
            "\t\tWHEN `USER_NAME` = \"南瓜\" THEN `PHONE`\n" +
            "\t\tELSE 3\n" +
            "\tEND AS F3\n" +
            "FROM\n" +
            "\tTB_USER";

    //exist
    String s9 = "select \n" +
            "* \n" +
            "from \n" +
            "`tb_user` tu \n" +
            "where\n" +
            "exists ( select count(1) from `tb_user` tu where tu.`phone` = \"14874542121\")\n" +
            "and tu.`user_name` like \"%xxx%\" ";

    // md5
    String s10 = "select \n" +
            "md5(`user_name`)\n" +
            "from tb_user";

    //date_add
    String s11 = "select date_add(`create_time` ,INTERVAL 7 DAY)\n" +
            "from tb_user";

    //group_concat
    String s12 = "select \n" +
            "group_concat(user_name,phone) as f1,\n" +
            "group_concat(user_name order by id desc) as f2 ,\n" +
            "group_concat(user_name separator ';') as f3,\n" +
            "group_concat(user_name,'-',phone order by id desc separator ';') as f4\n" +
            "from tb_user ";

    //LISTAGG
    String s13 = "\tselect \t\n" +
            "\t\tLISTAGG(USER_NAME , ',') WITHIN GROUP (ORDER BY id DESC) AS f1,\n" +
            "\t\tLISTAGG(USER_NAME , ',') AS f2\n" +
            "\tFROM TB_USER ";
    // 多字段in
    String s14 = "select * from tb_user tu \n" +
            "where (`user_name`,`phone`)in ((\"冬瓜\",\"18555555555\"))";

    //convert 这个函数没有对标的转换函数，仅做警告日志输出
    String s15 = "SELECT convert(tu.phone using utf8mb4) from tb_user tu";

    // in (select)
    String s16 = "select * from tb_user\n" +
            "where `phone` in (select `phone` from tb_user where `phone` like concat(\"%\",\"xxx\",\"%\"))";

    // 多字段 (f1,f2) in (select)
    String s17 = "select \n" +
            "* \n" +
            "from tb_user\n" +
            "where (`phone`,`user_name`) in (select `phone`,`user_name` from tb_user where `phone` like concat(\"%\",\"xxx\",\"%\"))";

    //多字段 in
    String s18 = "select \n" +
            "* \n" +
            "from tb_user\n" +
            "where (`phone`,`user_name`) in ((\"18555555555\",\"西\"),(\"18777777777\",\"瓜\"))";
    //函数(列) in
    String s19 = "select \n" +
            "* \n" +
            "from tb_user\n" +
            "where concat(`phone`,\"^\",`user_name`) in (\"18555555555^西\",\"18777777777^瓜\")";

    // STR_TO_DATE
    String s20 = "select STR_TO_DATE(create_time, '%Y-%m-%d %H:%i:%s') from tb_user ";

    //子查询没有别名
    String s21 = "SELECT * FROM( SELECT TMP.*, ROWNUM ROW_ID FROM ( SELECT * FROM tb_user a WHERE a.create_time > \"2021-01-02 15:12:01\" ORDER BY a.`create_time` DESC) TMP WHERE ROWNUM <= 1) WHERE ROW_ID > 10";

    // SUBSTRING_INDEX
    String s22 = "SELECT SUBSTRING_INDEX(\"www.example.com\", \".\", 2) ";

    // SUBSTRING_INDEX + GROUP_CONCAT
    String s23 = "select SUBSTRING_INDEX(GROUP_CONCAT( `user_name`),\",\",2) from tb_user ";

    //DATEDIFF
    String s24 = "SELECT DATEDIFF('2025-06-23','2025-07-25')";

    //BETWEEN
    String s25 = "select * from tb_user where role_id BETWEEN 1 and 100";

    //group by
    String s26 = "select * from tb_user where role_id BETWEEN 1 and 100 group by id,user_name";
    //test
    String s_test = "        SELECT\n" +
            "\t*\n" +
            "FROM\n" +
            "\t(\n" +
            "\tSELECT\n" +
            "\t\tTMP.*,\n" +
            "\t\tROWNUM ROW_ID\n" +
            "\tFROM\n" +
            "\t\t(\n" +
            "\t\tSELECT\n" +
            "\t\t\tso.id,\n" +
            "\t\t\tso.name,\n" +
            "\t\t\tso.short_name,\n" +
            "\t\t\tso.company_type,\n" +
            "\t\t\tso.type,\n" +
            "\t\t\tso.sign,\n" +
            "\t\t\tso.ext_no,\n" +
            "\t\t\tso.province,\n" +
            "\t\t\tso.city,\n" +
            "\t\t\tso.status,\n" +
            "\t\t\tso.available_date,\n" +
            "\t\t\tso.create_time,\n" +
            "\t\t\tsu.name creator,\n" +
            "\t\t\tso.parent_name belongOrg\n" +
            "\t\tFROM\n" +
            "\t\t\tsys_org so\n" +
            "\t\tLEFT JOIN sys_user su ON\n" +
            "\t\t\tso.create_by = su.id\n" +
            "\t\tWHERE\n" +
            "\t\t\tso.del_flag = 0\n" +
            "\t\t\tAND so.org_seq LIKE concat('%',' ?', '%')\n" +
            "\t\t\tAND so.type = 1\n" +
            "\t\tORDER BY\n" +
            "\t\t\tso.create_time DESC) TMP\n" +
            "\tWHERE\n" +
            "\t\tROWNUM <= 20)\n" +
            "WHERE\n" +
            "\trow_id > 1";

    //带有case when
    String u1 = "update tb_user \n" +
            "set `phone` = (CASE `phone` WHEN \"1\" THEN \"18111111111\" ELSE `phone` end )\n" +
            "where `id` = 5";

    //where 里面有函数
    String d1 = "DELETE FROM \n" +
            "tb_user \n" +
            "WHERE `phone` LIKE concat(\"%\",`user_name`,\"%\")";

    //同时插入多条数据
    String i1 = "insert into tb_user(`id`,`user_name`,`phone`) values(7,\"名字1\",\"18777777777\"),(9,\"名字2\",\"18999999999\")";

    //insert select
    String i2 = "insert into tb_user(`user_name`,`phone`)\n" +
            "select `user_name`,`phone` from tb_user where `id` > 5";

    //占位符
    String i3 = "INSERT INTO xxl_job_log (\n" +
            "        `job_group`,\n" +
            "        `job_id`,\n" +
            "        `trigger_time`,\n" +
            "        `trigger_code`,\n" +
            "        `handle_code`\n" +
            "        ) VALUES (\n" +
            "        ?,\n" +
            "        ?,\n" +
            "        ?,\n" +
            "        ?,\n" +
            "        ?\n" +
            "        );";

    // value ===> values  (jsqlparser语法解析的时候会自动把value 转换为values)
    String i4 = "INSERT INTO tb_user (id,USER_NAME,PHONE) value (7,'名字3',18777777777),(9,'名字5',18999999999)";

    //测试的sql
    String testSql = "SELECT \n" +
            " count(1) as transportSum, -- 运单总数数量\n" +
            "sum(IF(status = 0, 1, 0)) as orderSum, -- 已接单数量\n" +
            "sum(IF(status = 1, 1, 0)) as inGoodsSum, -- 装货中数量\n" +
            "sum(IF(status = 2, 1, 0)) as transportationSum, -- 运输中数量\n" +
            "sum(IF(status = 3, 1, 0)) as deliveredSum -- 已交货数量\n" +
            "FROM tms_transport\n" +
            "WHERE status in (0,1,2,3)\n" +
            "and del_flag = 0\n" +
            "AND create_date >= \"2021-01-01\"\n" +
            "AND \"2026-01-01\" >= create_date\n" +
            "AND org_id = 11111";

    /**
     * mysql转换为达梦的语法转换器测试
     *
     * @author liutangqi
     * @date 2025/5/22 11:01
     * @Param []
     **/
    @Test
    public void mysql2dmTransformation() throws JSQLParserException, NoSuchFieldException {
        //需要的sql
        String sql = s26;
        System.out.println("----------------------原始sql-----------------------");
        System.out.println(sql);
        //mock数据
        InitTableInfo.initTable();

        //初始化转换器实例缓存
        FieldProperties fieldProperties = new FieldProperties();
        TransformationProperties transformationProperties = new TransformationProperties();
        transformationProperties.setPatternType(TransformationPatternTypeConstant.MYSQL_2_DM);
        //测试开启强制小写转换
        transformationProperties.setForcedLowercase(true);
        fieldProperties.setTransformation(transformationProperties);
        new TransformationInstanceCache().init(fieldProperties);
        TableCache.init(new ArrayList<>(), fieldProperties);

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
        LRUCache<Integer, Integer> lruCache = CacheUtil.newLRUCache(3);
        for (int i = 0; i < 10; i++) {
            lruCache.put(i, i);
            lruCache.get(0);
            System.out.println(lruCache);
        }
    }


//----------------------------------------校验当前程序是否正确分割线---------------------------------------------------------

    //需要测试的sql
    List<String> sqls = Arrays.asList(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14,//s15,
            s16, s17, s18, s19, s20, s21, s22, s23,
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
    public void tfCheck() throws NoSuchFieldException, JSQLParserException, IllegalAccessException {
        //mock数据
        InitTableInfo.initTable();

        //初始化转换器实例缓存
        FieldProperties fieldProperties = new FieldProperties();
        TransformationProperties transformationProperties = new TransformationProperties();
        transformationProperties.setPatternType(TransformationPatternTypeConstant.MYSQL_2_DM);
        fieldProperties.setTransformation(transformationProperties);
        new TransformationInstanceCache().init(fieldProperties);

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
        //mock数据
        InitTableInfo.initTable();

        //初始化转换器实例缓存
        FieldProperties fieldProperties = new FieldProperties();
        TransformationProperties transformationProperties = new TransformationProperties();
        transformationProperties.setPatternType(TransformationPatternTypeConstant.MYSQL_2_DM);
        fieldProperties.setTransformation(transformationProperties);
        new TransformationInstanceCache().init(fieldProperties);

        for (String sql : sqls) {
            //开始解析sql
            //开始进行语法转换
            Statement statement = JsqlparserUtil.parse(sql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            String resultSql = transformationStatementVisitor.getResultSql();
            AnswerUtil.writeTfAnswerToFile(this, sql, resultSql);
        }
    }
}
