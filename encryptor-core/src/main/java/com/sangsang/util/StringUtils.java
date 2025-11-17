package com.sangsang.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.SymbolConstant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 避免引入多余的包，这里将commons.lang3的工具类给拷贝过来
 *
 * @copy org.apache.commons.lang3
 * @date 2024/3/29 15:25
 */
public class StringUtils {

    /**
     * <p>Checks if a CharSequence is empty (""), null or whitespace only.</p>
     *
     * <p>Whitespace is defined by {@link Character#isWhitespace(char)}.</p>
     *
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank("")        = true
     * StringUtils.isBlank(" ")       = true
     * StringUtils.isBlank("bob")     = false
     * StringUtils.isBlank("  bob  ") = false
     * </pre>
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is null, empty or whitespace only
     * @since 2.0
     * @since 3.0 Changed signature from isBlank(String) to isBlank(CharSequence)
     */
    public static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>Checks if a CharSequence is not empty (""), not null and not whitespace only.</p>
     *
     * <p>Whitespace is defined by {@link Character#isWhitespace(char)}.</p>
     *
     * <pre>
     * StringUtils.isNotBlank(null)      = false
     * StringUtils.isNotBlank("")        = false
     * StringUtils.isNotBlank(" ")       = false
     * StringUtils.isNotBlank("bob")     = true
     * StringUtils.isNotBlank("  bob  ") = true
     * </pre>
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is
     * not empty and not null and not whitespace only
     * @since 2.0
     * @since 3.0 Changed signature from isNotBlank(String) to isNotBlank(CharSequence)
     */
    public static boolean isNotBlank(final CharSequence cs) {
        return !isBlank(cs);
    }


    /**
     * 获取字符串中出现指定字符串的次数
     *
     * @author liutangqi
     * @date 2024/7/10 18:10
     * @Param [superstring 母串, substring 子串]
     **/
    public static int wordCount(String superstring, String substring) {
        int count = 0;
        if (StringUtils.isBlank(superstring) || StringUtils.isBlank(substring)) {
            return count;
        }

        int index = 0;
        while ((index = superstring.indexOf(substring, index)) != -1) {
            count++;
            // 移动到找到的子串之后
            index += substring.length();
        }
        return count;
    }


    /**
     * 将sql中的 ？ 替换为 DecryptConstant.PLACEHOLDER + 自增序号，从0开始
     *
     * @author liutangqi
     * @date 2024/7/10 18:05
     * @Param [sql]
     **/
    public static String question2Placeholder(String sql) {
        //找出原sql中的 ？个数
        int wordCount = StringUtils.wordCount(sql, SymbolConstant.QUESTION_MARK);
        for (int i = 0; i < wordCount; i++) {
            sql = sql.replaceFirst(SymbolConstant.ESC_QUESTION_MARK, FieldConstant.PLACEHOLDER + i);
        }
        return sql;
    }


    /**
     * 将sql中的 DecryptConstant.PLACEHOLDER + 自增序号，从0开始 替换为 ？
     *
     * @author liutangqi
     * @date 2024/7/10 18:06
     * @Param
     **/
    public static String placeholder2Question(String sql) {
        //找出原sql中的 DecryptConstant.PLACEHOLDER个数
        int wordCount = StringUtils.wordCount(sql, FieldConstant.PLACEHOLDER);
        for (int i = 0; i < wordCount; i++) {
            sql = sql.replaceFirst(FieldConstant.PLACEHOLDER + i, SymbolConstant.ESC_QUESTION_MARK);
        }
        return sql;
    }

    /**
     * 判断sql中是否一定不存在 lowerTableNames 中涉及的表
     *
     * @return true:一定不存在  false: 可能存在
     * @author liutangqi
     * @date 2025/7/3 10:40
     * @Param [sql, lowerTableNames]
     **/
    public static boolean notExist(String sql, Set<String> lowerTableNames) {
        //1.表名为空，或者sql为空 肯定不存在
        if (StringUtils.isBlank(sql) || CollectionUtils.isEmpty(lowerTableNames)) {
            return true;
        }

        //2.当前大小写不敏感的话将此sql转换为小写
        String disposeSql = sql;
        if (!TableCache.getCurConfig().isCaseSensitive()) {
            disposeSql = sql.toLowerCase();
        }

        //3.获取当前需要处理的表名，并根据当前项目配置进行大小写和关键符号的判断
        for (String tableName : lowerTableNames) {
            //3.1 判断当前大小写不敏感的话，将表名转换为小写
            String disposeTableName = tableName;
            if (!TableCache.getCurConfig().isCaseSensitive()) {
                disposeTableName = tableName.toLowerCase();
            }
            //3.2 去掉当前项目的标识符引用符
            disposeTableName = StringUtils.trimSymbol(disposeTableName, TableCache.getCurConfig().getIdentifierQuote());
            //3.3只要包含其中一个，就算
            if (disposeSql.contains(disposeTableName)) {
                return false;
            }

        }

        //4.都不包含
        return true;
    }

    /**
     * 判断sql中是否一定不存在 keyword 中涉及的关键字
     *
     * @author liutangqi
     * @date 2025/8/18 10:35
     * @Param [sql, keyword]
     **/
    public static boolean notExist(String sql, String keyword) {
        return notExist(sql, CollUtil.newHashSet(keyword));
    }

    /**
     * 将字符串中的整行的空白去除
     *
     * @author liutangqi
     * @date 2025/4/8 15:17
     * @Param [str]
     **/
    public static String replaceLineBreak(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        StringBuilder lineBuilder = new StringBuilder();
        boolean lineIsBlank = true;
        boolean hasContent = false; // 标记是否已有非空白内容
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // 检查换行符（支持 \n、\r、\r\n）
            if (c == '\n' || c == '\r') {
                // 处理 \r\n 组合的情况
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++; // 跳过 \n
                }
                // 若非空白行则保留
                if (!lineIsBlank) {
                    result.append(lineBuilder);
                    result.append(c);
                }
                // 重置行状态
                lineBuilder.setLength(0);
                lineIsBlank = true;
                hasContent = true;
                continue;
            }
            // 检查非空白字符
            if (!Character.isWhitespace(c)) {
                lineIsBlank = false;
            }
            // 添加到当前行
            lineBuilder.append(c);
        }
        // 处理最后一行无换行符的情况
        if (!lineIsBlank) {
            result.append(lineBuilder);
        }
        // 处理纯空白输入的情况
        else if (!hasContent && lineBuilder.length() > 0) {
            result.append(lineBuilder);
        }
        return result.toString();
    }

    /**
     * 获取字符串的md5值
     *
     * @author liutangqi
     * @date 2025/5/21 14:54
     * @Param [input]
     **/
    public static String getMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取sha256值
     *
     * @author liutangqi
     * @date 2025/5/27 11:03
     * @Param [input]
     **/
    public static String getSha256(String input) {
        return DigestUtil.sha256Hex(input);
    }


    /**
     * 获取sql可以标识唯一的串
     * 原sql长度_sha256
     *
     * @author liutangqi
     * @date 2025/5/29 14:22
     * @Param [sql]
     **/
    public static String getSqlUniqueKey(String sql) {
        return sql.length() + SymbolConstant.UNDERLINE + getSha256(sql);
    }


    /**
     * 根据当前配置是否大小写敏感，判断两个字段或者表名是否相等
     *
     * @author liutangqi
     * @date 2025/11/3 15:37
     * @Param [a, b]
     **/
    public static boolean fieldEquals(String a, String b) {
        //1.非空校验
        // 在此项目的场景中，只要有一个字符串是空的都可以判断两个是不等的，哪怕两个都是空字符串（正常流程中，是不存在两个都是空的情况的）
        if (StringUtils.isBlank(a) || StringUtils.isBlank(b)) {
            return false;
        }

        //2.当前配置大小写不敏感的话，则将字符串都转换为小写
        String compareA = a;
        String compareB = b;
        if (!TableCache.getCurConfig().isCaseSensitive()) {
            compareA = a.toLowerCase();
            compareB = b.toLowerCase();
        }

        //3.忽略当前项目的关键字符号，判断两个是否相等
        return equalsIgnoreKeywordSymbol(compareA, compareB);
    }

    /**
     * 忽略关键字的符号，判断两个字段是否相等
     * 注意：此方法没有非空校验
     *
     * @author liutangqi
     * @date 2025/11/3 16:03
     * @Param [a, b]
     **/
    private static boolean equalsIgnoreKeywordSymbol(String a, String b) {
        //从缓存中获取当前关键字的符号
        String identifierQuote = TableCache.getCurConfig().getIdentifierQuote();

        //去掉首尾的关键字
        String clearA = trimSymbol(a, identifierQuote);
        String clearB = trimSymbol(b, identifierQuote);

        //判断两个字符串是否相等
        return clearA.equals(clearB);
    }


    /**
     * 去除字符串的首尾符号
     * 注意：此方法非空判断仅判断null，不判断空字符串，高频调用，减少不必要判断逻辑
     *
     * @author liutangqi
     * @date 2025/11/3 17:04
     * @Param [str, symbol]
     **/
    public static String trimSymbol(String str, String symbol) {
        if (str == null || symbol == null) {
            return str;
        }

        int strLen = str.length();
        int symbolLen = symbol.length();

        // 如果字符串长度不足以包含首尾两个symbol，直接返回
        if (strLen < symbolLen * 2) {
            return str;
        }

        // 手动检查开头是否匹配
        boolean startsWithSymbol = true;
        for (int i = 0; i < symbolLen; i++) {
            if (str.charAt(i) != symbol.charAt(i)) {
                startsWithSymbol = false;
                break;
            }
        }
        // 如果开头不匹配，则直接返回
        if (!startsWithSymbol) {
            return str;
        }

        // 手动检查结尾是否匹配
        boolean endsWithSymbol = true;
        for (int i = 0; i < symbolLen; i++) {
            if (str.charAt(strLen - symbolLen + i) != symbol.charAt(i)) {
                endsWithSymbol = false;
                break;
            }
        }
        //如果结尾不匹配，直接返回
        if (!endsWithSymbol) {
            return str;
        }

        // 只有当首尾都包含symbol时才进行去除
        return str.substring(symbolLen, strLen - symbolLen);
    }


    /**
     * 判断两个sql是否相等
     * 仅用于自测
     *
     * @author liutangqi
     * @date 2025/11/12 14:18
     * @Param [sql1, sql2]
     **/
    public static boolean sqlEquals(String sql1, String sql2) {
        String compare1 = sql1;
        String compare2 = sql2;
        //1.当前配置了大小写不敏感，则统一转换为小写
        if (!TableCache.getCurConfig().isCaseSensitive()) {
            compare1 = sql1.toLowerCase();
            compare2 = sql2.toLowerCase();
        }

        //2.获取当前项目的关键字，去除sql中全部的关键字标识符
        String identifierQuote = TableCache.getCurConfig().getIdentifierQuote();
        compare1 = compare1.replaceAll(identifierQuote, SymbolConstant.BLANK);
        compare2 = compare2.replaceAll(identifierQuote, SymbolConstant.BLANK);

        //3.去掉sql中的逗号，查询的字段顺序不同时，最后一个字段会不带逗号，放前面这个逗号会不见
        compare1 = compare1.replaceAll(SymbolConstant.COMMA, SymbolConstant.BLANK);
        compare2 = compare2.replaceAll(SymbolConstant.COMMA, SymbolConstant.BLANK);

        //4.将sql按照空格切分后再比较，避免字段顺序不同导致判定两个sql不同
        Map<String, Integer> map = new HashMap<>();
        String[] split1 = compare1.split(SymbolConstant.SPACING);
        for (String s : split1) {
            Integer curCount = map.getOrDefault(s, 0);
            map.put(s, ++curCount);
        }
        String[] split2 = compare2.split(SymbolConstant.SPACING);
        for (String s : split2) {
            Integer curCount = map.get(s);
            if (curCount == null) {
                return false;
            }
            if (curCount < 1) {
                return false;
            }
            map.put(s, --curCount);
        }

        return map.values().stream().filter(f -> f > 0).count() == 0;
    }


}
