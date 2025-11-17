package com.sangsang.util;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.lang.Pair;
import cn.hutool.json.JSONUtil;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;

import java.util.List;
import java.util.Map;

/**
 * 自测sql时，获取答案相关的工具类
 *
 * @author liutangqi
 * @date 2025/3/6 14:47
 */
public class AnswerUtil {

    //当前的答案位置
    private static String answerBasePath = "/src/test/java/com/sangsang/answer/current/";
    //标准答案的位置
    private static String standardBasePath = "/src/test/java/com/sangsang/answer/standard/";

    /**
     * 将db模式处理好的sql写入到文件中
     *
     * @author liutangqi
     * @date 2025/3/6 14:02
     * @Param [obj:存放sql的对象 ,oldSql, resSql]
     **/
    public static void writeDBAnswerToFile(Object obj,
                                           String oldSql,
                                           String resSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + answerBasePath + "/db/" + fileName;
        FileWriter writer = new FileWriter(path);
        writer.write(resSql, false);
    }

    /**
     * 将pojo模式处理好的sql写入到文件中
     *
     * @author liutangqi
     * @date 2025/3/6 14:02
     * @Param [obj:存放sql的对象 ，oldSql, resSql]
     **/
    public static void writePOJOAnswerToFile(Object obj,
                                             String oldSql,
                                             List<FieldEncryptorInfoDto> fieldEncryptorInfos,
                                             Map<String, ColumnTableDto> placeholderColumnTableMap) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String listPath = projectRoot + answerBasePath + "/pojo/list/" + fileName;
        String mapPath = projectRoot + answerBasePath + "/pojo/map/" + fileName;
        FileWriter listWriter = new FileWriter(listPath);
        FileWriter mapWriter = new FileWriter(mapPath);
        listWriter.write(JSONUtil.toJsonStr(fieldEncryptorInfos), false);
        mapWriter.write(JSONUtil.toJsonStr(placeholderColumnTableMap), false);
    }

    /**
     * 将语法转换处理好的sql写入到文件中
     *
     * @author liutangqi
     * @date 2025/6/6 15:34
     * @Param [obj, oldSql, fieldEncryptorInfos, placeholderColumnTableMap]
     **/
    public static void writeTfAnswerToFile(Object obj,
                                           String oldSql,
                                           String resSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + answerBasePath + "/transformation/" + fileName;
        FileWriter writer = new FileWriter(path);
        writer.write(resSql, false);
    }

    /**
     * 将数据隔离的答案写入到文件中
     *
     * @author liutangqi
     * @date 2025/7/7 16:44
     * @Param [obj, oldSql, resSql]
     **/
    public static void writeIsolationAnswerToFile(Object obj,
                                                  String oldSql,
                                                  String resSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + answerBasePath + "/isolation/" + fileName;
        FileWriter writer = new FileWriter(path);
        writer.write(resSql, false);
    }

    /**
     * 写入字段默认值的答案
     *
     * @author liutangqi
     * @date 2025/11/14 9:31
     * @Param [obj, sql, resultSql]
     **/
    public static void writeFieldDefaultAnswerToFile(Object obj,
                                                     String oldSql,
                                                     String resSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + answerBasePath + "/fieldDefault/" + fileName;
        FileWriter writer = new FileWriter(path);
        writer.write(resSql, false);
    }


    /**
     * 读取db模式的这个sql的答案
     *
     * @author liutangqi
     * @date 2025/3/6 14:46
     * @Param [obj:存放sql的对象 ，oldSql]
     **/
    public static String readDBAnswerToFile(Object obj,
                                            String oldSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + standardBasePath + "/db/" + fileName;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(path);
        } catch (Exception e) {
            return null;
        }
        return fileReader.readString();
    }

    /**
     * 读取pojo模式的这个sql的答案
     *
     * @return key: list的答案 value: map的答案
     * @author liutangqi
     * @date 2025/3/6 14:46
     * @Param [obj:存放sql的对象 ，oldSql]
     **/
    public static Pair<String, String> readPOJOAnswerToFile(Object obj,
                                                            String oldSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String listPath = projectRoot + standardBasePath + "/pojo/list/" + fileName;
        String mapPath = projectRoot + standardBasePath + "/pojo/map/" + fileName;
        FileReader listFileReader = null;
        FileReader mapFileReader = null;
        try {
            listFileReader = new FileReader(listPath);
            mapFileReader = new FileReader(mapPath);
        } catch (Exception e) {
            return null;
        }
        return new Pair(listFileReader.readString(), mapFileReader.readString());
    }


    /**
     * 读取语法转换这个sql的答案
     *
     * @author liutangqi
     * @date 2025/3/6 14:46
     * @Param [obj:存放sql的对象 ，oldSql]
     **/
    public static String readTfAnswerToFile(Object obj,
                                            String oldSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + standardBasePath + "/transformation/" + fileName;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(path);
        } catch (Exception e) {
            return null;
        }
        return fileReader.readString();
    }

    /**
     * 读取数据隔离的答案
     *
     * @author liutangqi
     * @date 2025/7/7 16:46
     * @Param [obj, oldSql]
     **/
    public static String readIsolationAnswerToFile(Object obj,
                                                   String oldSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + standardBasePath + "/isolation/" + fileName;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(path);
        } catch (Exception e) {
            return null;
        }
        return fileReader.readString();
    }

    /**
     * 读取字段默认值的答案
     *
     * @author liutangqi
     * @date 2025/11/14 9:57
     * @Param [obj, oldSql]
     **/
    public static String readFieldDefaultAnswerToFile(Object obj,
                                                      String oldSql) throws IllegalAccessException {
        //项目路径
        String projectRoot = System.getProperty("user.dir");
        //获取变量名作为文件名
        String fileName = ReflectUtils.getFieldNameByValue(obj, oldSql);
        String path = projectRoot + standardBasePath + "/fieldDefault/" + fileName;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(path);
        } catch (Exception e) {
            return null;
        }
        return fileReader.readString();
    }
}
