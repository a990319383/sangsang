# field-encryptor使用文档

## 一、技术支持

- 990319383@qq.com
- bug反馈可通过邮件或者QQ的方式进行联系，QQ的话请备注来意

## 二、简介

> 用于等保，秘评，信创，数据安全等场景。实现业务快速改造，减少重复性工作，使客户专注业务开发
>
> 基于mybatis拦截器 + jsqlparser ，动态改写sql。
>
> 此文档针对3.6.0以后的版本
>
> 请使用最新版本，没有特殊情况，bug都会在最新版本修改。当前最新git版本为3.6.0,还在测试阶段，中央仓库最新版本为3.5.3-alpha 

## 三、功能清单

> - 数据库字段自动加解密（自动改写sql实现加密存储，解密查询）
>   - db模式：使用数据库自带的加解密算法
>   - pojo模式：使用java可以实现的加解密算法
> - sql语法自动切换
>   - mysql的语法自动转换为达梦数据库语法
> - 业务数据自动隔离
>   - 执行的sql自动拼接上权限隔离的条件，可轻松解决复杂场景的数据隔离
> - 数据变更时维护默认值
>   - 数据新增，修改时自动填充默认值，可用于创建修改时间，租户等场景
> - 数据库字段查询脱敏
>   - 在sql层对字段进行脱敏处理，业务程序中获取到脱敏后的结果
>   - 与常见的序列化层脱敏仅处理层不同，酌情根据自己情况使用，不推荐用此方案
>   - 此功能可能略显鸡肋，后续版本或考虑移除

## 四、功能介绍

### 1.数据库字段自动加解密

#### 应用场景

> 用于敏感数据数据库存储加密的项目改造
>
> 能解决等保，秘评，数据安全等常见问题，实现项目快速改造

#### 方案对比

|          | 一般方案                                                     | sharding-sphere                               | 本框架                                 |
| -------- | ------------------------------------------------------------ | --------------------------------------------- | -------------------------------------- |
| 使用方式 | 基于拦截器，在mapper的入参和响应进行标注，实现自动加解密     | 配置文件中指定哪些表哪些字段用什么算法        | 仅需在实体类上标注哪些字段需要密文存储 |
| 效率     | 改造繁琐，对业务代码有侵入                                   | 快捷改造，业务代码0侵入，类似本框架的pojo模式 | 快捷改造，业务代码0侵入                |
| 示栗     | 假设字典表的名字需要密文存储，有100个sql用到了，需要标注100次 | 仅需配置文件中配置一次                        | 仅需在字典表实体类字段上标注1次即可    |

#### 模式介绍

>  框架总共有两种加密模式

- db
  - 特点
    - **依赖数据库的加解密算法**，通过自动改造原sql，在对应字段自动调用库函数，实现字段的自动加解密
  - 优点
    - 加解密场景适应性强，支持列运算的字符加解密
    - **支持密文模糊查询**
  - 缺点
    - 原生数据库的加解密函数较少，但是可以通过自己扩展udf解决这个问题
    - 额外增加数据库的计算开销（数据库会多几个函数运算，这个看项目场景，一般场景可忽略）
- pojo
  - 特点
    - **依赖java库的加解密算法**，通过动态修改mapper的入参响应，实现字段的自动加解密
  - 优点
    - 加解密算法可选择性强，java能实现的算法都支持
  - 缺点
    - 部分场景不兼容（详见 --> 不兼容的场景）
    - 一般情况下不支持模糊查询（除非选用特定的加密算法）

#### 效果示栗

> 假设tb_user的phone字段是需要加密存储的，经过配置之后，所有涉及到tb_user表的数据插入会自动加密，数据读取会自动解密

```mysql
# 其中 phone 字段是密文存储的
select  phone,user_name FROM tb_user WHERE phone = ?
```

- db模式

  拦截器会自动把sql替换成下面的

  ```sql
  -- select查询的会进行解密转换  where条件的会进行加密转换
  SELECT 
  CAST(AES_DECRYPT(FROM_BASE64(phone), '7uq?q8g3@q') AS CHAR) AS phone,
  user_name 
  FROM tb_user 
  WHERE phone = TO_BASE64(AES_ENCRYPT(?, '7uq?q8g3@q'))
  ```

- pojo模式

  原sql不做变更，在拦截器这层，对入参进行加密，拿到sql结果集后，对响应进行解密

#### 不兼容场景

##### db,pojo模式均不兼容的场景

- 项目基于jsqlparser4.9解析，其版本不支持的sql语法，此框架无法兼容
- INSERT INTO 表名 VALUES (所有字段);   表名后面没有跟具体字段的，无法兼容

##### pojo模式不兼容的场景

- mybatis-plus  service层自带的saveBatch()方法不支持自动加密
- 列运算的结果集和sql的入参响应需要做对应的
  - 例如： select  concat(phone,"---")  as ph from tb_user;  无法将ph变量做自动的解密映射
- 同一个#{}入参，sql中对应不同的字段，想要拥有不同的值

### 2.sql语法自动切换

#### 应用场景

> 信创大环境下，有些项目原本使用的mysql数据库，现在需要整个切换到达梦数据库中
>
> 目前仅支持mysql自动转达梦，后续其它数据库的自动转换后续版本会规划

#### 方案对比

|          | 硬改项目sql                                      | 本框架                                                       |
| -------- | ------------------------------------------------ | ------------------------------------------------------------ |
| 效率对比 | 满项目找，改动大                                 | 仅需引入依赖，改好配置即可                                   |
| 覆盖率   | 一个不支持的语法，需要满项目找，不一定能全部改完 | 只要覆盖了一种语法的转换，整个项目这种语法的都会进行自动转换 |

#### 效果示栗

> 开启了语法转换后，目前已经兼容的语法，全局mysql的语法会自动转换成达梦的语法，无需针对sql进行调整

### 3.业务数据自动隔离

#### 应用场景

> 用于一般的项目开发中，需要不同的登录用户看到不同的数据范围

#### 方案对比

|          | 一般方案                                                     | 本框架                                                       |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 使用对比 | 封装一个注解，标注在方法上或者具体的mapper上，上面指定数据隔离字段，进行隔离。或者借助mybatis-plus的DataPermissionHandler，针对不同的sql标注，进行隔离 | 仅需在实体类上标注即可                                       |
| 项目规范 | 封装的注解，团队中可能有的人会有，有的人不用自己sql拼，不统一，后续权限改动的话，改造困难 | 仅建表的人写即可，业务开发人员大家都不自己写，也不用自己标注解，项目统一 |
| 扩展性   | 一般的封装对不同场景支持较弱，有的场景需要 = 有的需要like，有的需要in，而且有的需要同一个sql不同场景下使用不同字段进行隔离 | 均支持                                                       |

#### 效果示栗

> 场景：假设表 tb_user 的数据隔离PC端是使用org_id，根据当前登录用的org_id进行隔离，app端是根据id进行数据隔离

注意：配置好后，所有涉及到tb_user查询都会默认加上数据隔离的条件(有个例想不用的见快速接入的进阶使用)

```
假设原程序中的执行sql如下：
  select * from tb_user where phone = 'xxx' or name = 'yyy'
经过合理配置后(见快速使用)，所有涉及到tb_user的查询，都会自动拼接上上述规则，上面的sql会变成
PC端： 
  select * from tb_user where (phone = 'xxx' or name = 'yyy') and org_id = 登录用户组织id
APP端：
  select * from tb_user where (phone = 'xxx' or name = 'yyy') and id = 登录用户id
```

### 4.数据变更时维护默认值

#### 应用场景

> 一些固有字段，每次新增，或者修改时都会设置固定的值
>
> 配合功能4的数据隔离，可以轻松实现项目的数据隔离管理
>
> 栗如：
>
> ​	创建时间，修改时间，创建人，修改人      
>
> ​	数据隔离字段(组织id)    租户id

#### 方案对比

|          | 一般方案                                                    | 本框架                 |
| -------- | ----------------------------------------------------------- | ---------------------- |
| 使用方式 | 利用mybatis-plus的MetaObjectHandler，在实体类字段上标注即可 | 在实体类字段上标注即可 |
| 适配场景 | 必须要求插入和修改的入参类拥有需要维护的字段才行            | 无限制                 |

#### 效果示栗

> 场景：有一张订单表现在突然有需求，需要在PC端按照部门进行数据隔离，所以我需要记录下每笔订单生成时的所属部门是谁

```
原有业务sql:
  insert into 订单表(字段1，字段2...) values（值1，值2...） 里面并没有新增的部门字段
增加配置之后，所有涉及到订单表的新增语句都会自动加上部门字段
实际执行sql:
  insert into 订单表(部门字段，字段1，字段2...) values（登录用户部门，值1，值2...）
```

### 5.数据库字段查询脱敏

#### 应用场景

> 用于敏感数据脱敏展示
>
> 与常见的序列化脱敏方案比较，进行业务差异化脱敏时不需要额外配置。其它的区别主要是在脱敏的阶段

#### 方案对比

|          | 序列化脱敏                                                   | 本框架                                                       |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 使用方式 | 对接口返回类字段进行标注                                     | 对sql的返回类字段进行标注                                    |
| 适用场景 | 可针对单个字段，也可经过特殊配置后获取到整个对象后进行业务差异化脱敏 | 本身支持对单个字段，或者获取到整个对象后对业务进行差异化脱敏 |
| 示栗     | 有个单据数据需要展示物料名字，但是A业务类型很特殊，需要对物料名字进行脱敏展示，其它业务类型不需要脱敏，此方案实现此场景需要额外配置 | 能获取到整个响应对象，判断是否是A业务，进行差异化脱敏        |

#### 效果示栗

> 场景：我现在返回了一批订单的物料详情数据，但是其中A类型订单的物料属于敏感物料，不能让app端的客户直接看到原物料名字A，需要统一脱敏展示成物料名字* ，但是其它类型的单子要求展示原名字

```
经过合理配置标注之后的订单列表如下，实现同一接口的list进行差异化脱敏
  订单_001  订单类型A   脱敏物料名字*
  订单_002  订单类型A   脱敏物料名字*
  订单_003  订单类型B   原本的物料名字
```

#### 实现原理

> 使用mybatis拦截器，将sql执行结果的结果集进行脱敏处理

## 五、快速接入

### 全局配置

>  引入依赖

- 一般情况，仅需引入下面依赖即可

  ```xml
  <dependency>
      <groupId>io.gitee.tired-of-the-water</groupId>
      <artifactId>encryptor-core</artifactId>
      <version>对应版本</version>
  </dependency>
  ```

- 当实体类模块没有mybatis依赖时，可以在实体类模块单独引入注解模块，用于使用框架注解

  ```xml
  <dependency>
      <groupId>io.gitee.tired-of-the-water</groupId>
      <artifactId>encryptor-annos</artifactId>
      <version>对应版本</version>
  </dependency>
  ```

### 各功能特殊配置

#### 1.数据库字段自动加解密

> 进行了“基本配置” 和“字段标注” 即可使用

##### 基本配置

```
#配置@TableName标注的实体类路径
field.scanEntityPackage[0]=com.sangsang.*.entity
#确定想要使用的模式是哪种，目前支持 db  pojo 两种模式配置
field.encryptor.patternType=db
#自定义秘钥(这里配置秘钥后，默认的加密算法会使用这个秘钥，也可不配置，有默认秘钥)
field.encryptor.secretKey=TIREDTHEWATER
```

##### 字段标注

> 在标注了@TableName的实体类中，找到自己需要密文存储的字段使用@FieldEncryptor标注
>
> 备注：若项目中不存在实体类，在扫描路径下建一个，仅保留需要标注的字段（仍然需要@TableName标注类）

```java
@Data
@TableName(value = "tb_user")
public class UserEntity extends BaseEntity {

    @TableField(value = "phone")
    @FieldEncryptor //标识这个字段是加密的字段
    private String phone;
}
```

#### 2.sql语法转换

> 仅需基本配置完成即可完成语法的自动切换
>
> 注意：完整切换到其它数据库的话，还需要改项目的数据库驱动和连接地址

##### 基本配置

```
#配置@TableName标注的实体类路径
field.scanEntityPackage[0]=com.sangsang.*.entity
#语法转换模式指定，目前仅支持mysql转达梦
field.transformation.patternType=mysql2dm
```

#### 3.业务数据自动隔离

> 完成 基本配置  自定义数据隔离策略 标注 即可快速使用

##### 基本配置

```
#配置@TableName标注的实体类路径
field.scanEntityPackage[0]=com.sangsang.*.entity
#开启数据隔离
field.isolation.enable=true
```

##### 自定义数据隔离策略

> 注意1：和加解密类似，整个spring容器中如果只有一个DataIsolationStrategy子类，则这个就是默认策略
>
> 注意2：项目spring容器中存在多个DataIsolationStrategy子类，@DefaultStrategy标注那个是默认策略

```java
package xxx.strategy;

import com.sangsang.domain.enums.IsolationRelationEnum;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;
import org.springframework.stereotype.Component;

/**
 * @author liutangqi
 * @date 2025/7/4 17:12
 */
@Component
public class TIsolationBeanStrategy implements DataIsolationStrategy<Long> {
    //返回需要进行数据隔离的表字段名字 
    //这里入参的tableName可以获取到当前是哪张表，一般是表名
    //一般项目会将登录用户存threadlocal中，这里可以取出来，根据不同的登录用户选择不同的字段隔离
    @Override
    public String getIsolationField(String tableName) {
        return "org_id";
    }

    //目前支持 "="  "in"  "like 'xxx%'" 三种模式
    @Override
    public IsolationRelationEnum getIsolationRelation(String tableName) {
        return IsolationRelationEnum.EQUALS;
    }

    //从当前登录用户的theadlocal中获取到用于数据隔离的字段即可，比如当前登录用户组织id之类的
    @Override
    public Long getIsolationData(String tableName) {
        return 777777777L;
    }
}
```

##### 标注

> 标注在实体类上，一个实体类表示一种抽象数据，一种数据的权限所属一般情况是固定的
>
> 一个@DataIsolation 可以同时指定多个策略，并且可以指定多个策略之间是and 还是 or

```java
/**
 * @author liutangqi
 * @date 2025/7/2 14:45
 */
@TableName("tb_order")
@Data
//使用上面的默认策略，根据org_id进行数据隔离，只能看到同组织的单据，注意，此时实体类字段必须写上orgId这个字段
@DataIsolation
public class OrderEntity extends BaseEntity {

    @TableField("org_id")
    private Long orgId;

}
```

#### 4.数据变更时维护默认值

##### 基本配置

```
#配置@TableName标注的实体类路径
field.scanEntityPackage[0]=com.sangsang.*.entity
#开启数据变更时设置默认值
field.fieldDefault.enable=true
```

##### 自定义默认值策略

```
/**
* 创建时间默认值
*
* @author liutangqi
* @date 2025/7/24 14:20
* @Param
**/
public  class CreateTimeStrategy implements FieldDefaultStrategy<LocalDateTime> {

    @Override
    public boolean whetherToHandle(SqlCommandEnum sqlCommandEnum) {
        //新增时设置默认值
        return SqlCommandEnum.INSERT.equals(sqlCommandEnum);
    }

    @Override
    public LocalDateTime getDefaultValue() {
        return LocalDateTime.now();
    }
}
```

##### 标注

```
/**
*
*这个是所有实体类的基类，也可以直接标注在实体类(@TableName)上面
*/
public class BasePo {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建者
     */
    @FieldDefault(CreateTimeStrategy.class)//新增时维护默认值
    private String createBy;

    ...略
}
```

#### 5.数据库自动查询脱敏

> 完成 基本配置 自定义脱敏策略 和字段标注即可开始使用

##### 基本配置

```
#开启脱敏支持
field.desensitize.enable=true
```

##### 自定义脱敏策略

```java
import com.sangsang.domain.strategy.desensitize.DesensitizeStrategy;

/**
 * @author liutangqi
 * @date 2025/7/8 13:45
 */
public class TestDesensitizeStrategy implements DesensitizeStrategy {
    //注意： s可能为null，自己根据业务进行不同处理
    @Override
    public String desensitize(String s, Object o) {
        //s 是待脱敏的字符串
        //o 是一整个返回对象，可以根据o来做一些业务差异化脱敏
        return "脱敏后：" + s;
    }
}
```

##### 字段标注

> 注意：这个是标注在sql的返回对象上，不是实体类，区别于其它功能

- 响应是实体类：在具体字段上面标注@FieldDesensitize 并指定算法

  ```
  public class UserVo{
  	private Long id;
      /**
       * 用户名
       */
      @FieldDesensitize(TestDesensitizeStrategy.class)
      private String userName;    
  }
  ```

- 响应是String：在mapper上标注@MapperDesensitize 并指定算法

  ```
     @MapperDesensitize(@FieldDesensitize(TestDesensitizeStrategy.class))
      List<String> getListResult(String name);
  ```

- 响应是Map：在mapper上标注@MapperDesensitize 并指定算法，字段名

  - 注意：fieldName指定的是sql中结果集的变量名(as 后面是什么就是什么，没有as 的话就是表达式或字段名)

  ```java
     @MapperDesensitize({@FieldDesensitize(value = TestDesensitizeStrategy.class, fieldName = "user_name"),
              @FieldDesensitize(value = TestDesensitizeStrategy.class, fieldName = "xxx")})
      Map getResultMap(String name);
  ```



## 六、进阶使用

### 全局配置

- 配置sql解析LRU缓存容量（默认值500）

  > 项目中需要使用到的sql会经过jsqlparser进行解析，遇到复杂的sql解析，这个操作会比较耗时(大约10~40ms)，本地会对解析结果进行一个LRU的缓存，默认缓存容量是500

  ```
  field.lruCapacity=700  //设置LRU缓存容量700
  ```

- 自动通过DataSource读取当前项目连接库的表结构信息 （默认开启）

  > 本框架对sql进行解析时，大部分功能需要获取到表的所有字段信息，会将这部分信息缓存到本地
  >
  > 开启此配置时，会自动读取当前项目连接数据库的表结构信息，并将需要的进行缓存
  >
  > 当启动报错无法获取时，关闭此配置，手动在扫描类的@TableName 类上，将表最新的字段信息补全
  >
  > 备注：如果需要手动配置的话，语法解析功能需要整个库全部表字段信息，加解密功能需要涉及的表的全部表字段信息，数据隔离和默认值仅需要涉及到的表所涉及的字段信息

  ```
  field.autoFill=false  //设置不从DataSource中自动读取表结构信息
  ```

- 数据库大小写敏感配置(默认大小写不敏感)

  > 本框架很多功能需要保证sql中的表字段是你配置的表字段，这期间有个匹配关系，判断是否是同一字段的时候，此配置会影响匹配逻辑  
  >
  > 栗如： user_name 和USER_NAME 在大小写敏感配置下，就不属于同一个字段

  ```
  field.caseSensitive=true  //开启大小写敏感
  ```

- 数据库标识符的引用符配置（默认从spring的第一个DataSource中自动获取）

  > 数据库表名和字段名和关键字冲突时，需要使用标识符引用符将关键字包裹起来
  >
  > 栗如：mysql的是 `  达梦，oracle等的是 "
  >
  > 很多功能需要保证sql中的表字段是你配置的表字段，这期间有个匹配关系，判断是否是同一字段的时候，此配置会影响匹配逻辑，此配置一般情况下不用调整，会自动从当前spring的DataSource的第一个中读取，如果用了语法转换功能的话，比如mysql2dm，则默认是按照mysql的 ` 为准

  ```
  field.identifierQuote="  //设置标识符引用符是 "
  ```

### 各功能特殊配置

#### 1.数据库字段自动加解密

##### 自定义加密算法

- db模式

  > 注意1: 所有引入的jsqlparser的包是com.shade.net.sf.jsqlparser 开头的，使用本框架打的包，不要使用自己引的net.sf.jsqlparser包下的
  >
  > 注意2：如果当前项目想要配置多种算法的话，需要在默认的算法的类上面标注@DefaultStrategy

```java
package xxx.strategy;

import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.annos.DefaultStrategy;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import com.shade.net.sf.jsqlparser.expression.Expression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author liutangqi
 * @date 2025/7/4 14:02
 */
@Component
@DefaultStrategy
public class DBFieldEncryptorStrategrDefault implements FieldEncryptorStrategy<Expression> {
  
    @Autowired
    private FieldProperties fieldProperties;
	// 关于如何使用jsqlparser 拼凑出数据库的加解密函数
    //参考com.sangsang.encryptor.db.DefaultDBFieldEncryptorPattern
    
    @Override
    public Expression encryption(Expression oldExpression) {
          //todo 加密的逻辑 秘钥最好使用fieldProperties配置的秘钥
        return null;
    }

    @Override
    public Expression decryption(Expression oldExpression) {
         //todo 解密的逻辑 秘钥最好使用fieldProperties配置的秘钥
        return null;
    }
}

```

- pojo模式

```java
package xxx.strategy;

import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author liutangqi
 * @date 2025/7/8 11:17
 */
@Component
public class PojoFieldEncryptorStrategy implements FieldEncryptorStrategy<String> {
    @Autowired
    private FieldProperties fieldProperties;
    @Override
    public String encryption(String s) {
        //todo 加密的逻辑 秘钥最好使用fieldProperties配置的秘钥
        return null;
    }

    @Override
    public String decryption(String s) {
        //todo 解密的逻辑 秘钥最好使用fieldProperties配置的秘钥
        return null;
    }
}

```

##### 项目同时存在多种加密算法

参考上面的“自定义加密算法”，项目中实现多种算法，并放在spring容器中

- 当项目spring容器中只有一个算法，则这个算法就是默认算法
- 当spring容器中存在多个算法，需要使用@DefaultStrategy标注在算法类上作为默认算法
- 当字段使用默认算法时@FieldEncryptor 后面不用指定，使用其它算法时，需要这个注解后面指定算法类

##### 分库分表项目集成

- @FieldEncryptor 字段标注是记录了哪些表的哪些字段需要进行密文存储，当不进行任何配置时，需要将每张分表都创建好实体类，并在上面的每个字段都标注好
- 简化配置
  - 栗子："stat_vehicle_alarm"这张表是分表前的表名。
    - 实际数据库中不存在这张表
    - 数据库中有的表名是"stat_vehicle_alarm_0101","stat_vehicle_alarm_0102"等根据天进行了分表的
  - 实现ShardingTableStrategy接口，在里面配置根据原表名获取到分表的表名的规则
  - 在原表的实体类上标注@ShardingTableEncryptor(value = ShardingTableByDay.class)

```java
//原表实体类
@ShardingTableEncryptor(value = ShardingTableByDay.class)
@TableName("stat_vehicle_alarm")
public class StatVehicleAlarmEntity {
    //xxx字段
}

//分表表名规则
public class ShardingTableByDay implements ShardingTableStrategy {
    private static final LocalDate BASE_DATE = LocalDate.parse("2000-01-01");
    private static final DateTimeFormatter DATE_FMT = DatePattern.createFormatter("MMdd");
    private static final List<String> SUFFIX;
    static {
        SUFFIX = new ArrayList<>();
        for (int i = 0; i < 366; i++) {
            SUFFIX.add(LocalDateTimeUtil.format(BASE_DATE.plusDays(i), DATE_FMT));
        }
    }

    @Override
    public List<String> getShardingTableName(String prefix) {
        return SUFFIX.stream().map(suffix -> prefix + suffix).collect(Collectors.toList());
    }
}
```

##### pojo模式强制指定响应解密

> 当pojo模式遇到不兼容的场景，但是还是想要在查询结果进行解密处理时

```
# 在sql的响应类的字段上面标注这个注解
# 注意:这里是响应类，不是实体类！！！
@PoJoResultEncryptor
```

#### 2.sql语法转换

##### 表，字段强制转小写

> 当达梦数据库配置的是大小写敏感，但是旧mysql项目中存在大写的关键字，在保证达梦数据库所有表名，字段名统统小写的前提下，可以开启下面配置，将项目的sql字段，表名全部转小写

```
field.transformation.forcedLowercase=true //不建议开启此配置，可用于临时调试使用
```

##### 自己扩展语法转换器

> 当本框架忽略了某些语法的转换，想自己在项目中进行扩展增强

```
这个包下面有各种类型的基类语法转换器
step1: 
	创建一个这个包路径com.sangsang.transformation.mysql2dm
		注意：mysql2dm这个包路径是当前配置语法转换的模式名字，想要实现其它数据库语法转换的话，比如oracle2mysql，则配置field.transformation.patternType=oracle2mysql，自己实现的扩展就写在com.sangsang.transformation.oracle2mysql包下
step2:
	找到自己想要改写的语法属于column,table,function还是什么，在对应的包路径下实现对应的基类转换器，里面实现自己的逻辑，详情可参考源码
```

#### 3.业务数据自动隔离

##### 禁用数据隔离

> 当某些场景下，不想系统默认加上数据隔离，就想看到全部数据时
>
> 使用@ForbidIsolation进行标注

- 单个sql不想要数据隔离，标注在mapper上

```
@ForbidIsolation
List<OrderEntity> getPage();
```

- 整个service的方法都不想要数据隔离,标注在service上

```
 @ForbidIsolation
 public void test(){
     //各种sql 各种业务都不想要数据隔离
 }
```

##### 配置多个数据隔离策略之间的关系是属于and还是or

- 一个sql中多个表均有数据隔离条件，在全局配置中配置

```
#一个sql中多个表均有数据隔离条件，这里配置不同表的隔离之间的关系，默认是 and
field.isolation.conditionalRelation=or
```

- 一个sql中同一张表有多个数据隔离条件，在这个表的@DataIsolation中使用conditionalRelation进行配置

```
#一个sql中同一张表有多个数据隔离条件，这里配置不同策略之间的关系，默认是 and
@TableName("sys_user")
@DataIsolation(conditionalRelation = IsolationConditionalRelationEnum.OR, 
value = {策略1.class, 策略2.class})
public class SysUserEntity {
    ... 略
}
```

##### 数据隔离in出现过多字段时进行分段

> 当某些业务因为各种原因不可避免的出现数据隔离字段in (xxx,xxx) 的这个字段长度很大，oracle默认超过1000条就会报错，此时可以修改配置，会自动将这个进行分段
>
> 栗子： 原本是  id in (xxx,xxx,xxx 共计1000个)，分段后 (id in (xxx,xxx共计500个) or id in (xxx,xxx共计500个))

```
field.isolation.inRelationSubsectionSize=500 //默认值就是500，可以根据自己情况调，一般不需要改
```

##### 复杂场景的业务隔离

- 模拟系统简介
  - PC端系统中存在 一级组织 --> 二级组织---> 三级组织 
    - 每一级只能看到自己和下级的单据
    - 此时通过org_seq进行隔离
  - H5端或者App端 有司机
    - 每个司机登录只能看到自己的单据
- 系统简单设计
  - 权限系统
    - org_seq 这个字段存储  上级组织路径-自己组织id
    - 一级组织： 一级组织id
    - 二级组织： 一级组织id-二级组织id
    - 三级组织： 一级组织id-二级组织id-三级组织id
  - 登录系统
    - 用户登录，将当前用户的类型（是司机还是一，二，三级组织）存到threadlocal
    - 是一二三级组织的话，存一份org_seq 司机的话存一份司机id
- 自定义策略实现

```
@Component
public class TIsolationBeanStrategy implements DataIsolationStrategy<String> {
   
    @Override
    public String getIsolationField(String tableName) {
    	//从threadLocal中获取用户类型，是司机的话就返回 司机id字段(driver_id)否则返回 org_seq
        return null;
    }

    //目前支持 "="  "in"  "like 'xxx%'" 三种模式
    @Override
    public IsolationRelationEnum getIsolationRelation(String tableName) {
    //根据当前登录用户类型，司机的话就是  "="  组织的话就  "like 'xxx%'"
        return null;
    }
   
    @Override
    public String getIsolationData(String tableName) {
    //根据当前登录用户类型，返回登录用户的driverId或者orgSeq即可
        return null;
    }
}
```

#### 4.数据变更时维护默认值

##### 强制覆盖原sql的值

> 当我们需要设置的值在原业务sql中存在，我们不想要业务sql的值，想要强制用我们策略的值覆盖sql的值

```
# 进行字段标注的时候，使用mandatoryOverride为true，设置强制覆盖原值
@FieldDefault(value = CreateTimeStrategy.class,mandatoryOverride = true)
```

##### 配合数据隔离实现隔离字段自动写入，查询自动增加隔离条件

> 继续六、进阶使用---3.业务数据自动隔离---复杂场景的业务隔离的场景

​	我们接入了数据隔离功能后，数据查询会自动增加上数据隔离条件，但是数据写入仍然需要手动去维护

此时我们可以用上本功能，在对应字段上标注，自动维护org_seq的值，实现真正的全自动0侵入数据隔离

#### 5.数据库自动查询脱敏

> 暂无

## 七、常见问题Q&A

### Q1项目没有使用mybatis-plus，没有实体类这个概念，怎么快速接入

#### 情况一：项目中有权限可以通过DataSource读取到表结构

> 保证没有改动过 field.autoFill=true 
>
> 在配置的扫描路径下建一个文件夹，里面将用到的表创建好@TableName标注的实体类，仅需要写上用到的表字段，并进行相关功能的注解标注

#### 情况二：没得权限

> 可以单独建立一个文件夹，利用第三方工具（比如代码生成器）将整个表的字段信息导入到指定文件夹
>
> 也可以使用本项目提供的生成实体类工具（目前仅用于mysql和oracle，其它数据库尚未测试过）

```
-- 提供的专门的工具类，可以生成实体类
com.sangsang.util.EntityGenerateUtil#generateEntity(GenerateDto dto)
-- 栗子
EntityGenerateUtil.generateEntity(GenerateDto.builder()
    .packageName("com.sangsang.es.entity")//生成实体类包名
    .outputDir("D:\resource\draft\test")//生成实体类路径
    .url("jdbc:mysql://127.0.0.1:3306/your_database")//数据库地址
    .username(username)//数据库账号
    .password(password)//数据库密码
    .catalog("sjj-dts")//目录，可不传
    .schemaPattern(null)//模式：Oracle一般传用户名/模式名（大写）;mysql一般传null
    .tableNamePattern("%")//表名过滤规则，% 匹配任意多个字符  _ 匹配单个字符 null表示不限制
    .build());
```

### Q2:项目没有使用mybatis-plus，新写的实体类，我不想把字段写全，有什么影响

#### 情况一：项目中有权限可以通过DataSource读取到表结构

> 跳过不用管，你不会有这个问题

#### 情况二：没得权限

> 如果使用到的功能涉及到表字段的，必须要求涉及到的字段存在，该表的其它字段不存在会影响部分功能

- 1.数据库字段自动加解密

  - db模式
    - 假设A表，实体类字段仅写了一部分，且A表中涉及到需要密文存储的字段
    - 此时遇到 select * 的场景，会导致字段缺失
  - pojo模式
    - 暂未发现影响

- 2.数据库自动查询脱敏

  > 此功能和实体类标注无关

- 3.sql语法转换

  - 如果涉及到语法转换的表字段没有标注全的话，涉及到对字段的语法转换将无法正常转换
  - 栗子：
    - mysql 查询的字段是 `字段`  到达梦需要变成  "字段"
    - 如果实体类这个字段缺失，会导致缺失的字段没有正常转换，导致sql执行报错

- 4.业务数据自动隔离

  - 隔离字段必须存在，其它字段不存在，暂未发现影响

- 5.数据变更时维护默认值

  - 需要维护的字段必须存在，其它字段不存在，暂未发现影响

### Q3:我项目已经存在了，我想利用db模式对项目进行加密，历史数据清洗有没有什么建议

> 项目提供了生成备份，回滚的脚本生成逻辑可供参考
>
> 具体根据自己项目数据量权衡具体实现方式

```
-- 再自己配置好的项目中调用这个方案
-- 注意：确保自己项目密文存储字段标注完成，并确保spring环境启动好了，再调用下面方法
com.sangsang.bak.BakSqlCreater#bakSql()

-- 栗子
BakSqlCreater.bakSql("jdbc:mysql://127.0.0.1:3306/your_database",//数据库地址
    username,//数据库用户名
    password, //数据库密码
    suffix,//备份表后缀
    expansionMultiple,//原表名扩容倍数，建议值 5 
    "D:\resource\draft\test")//脚本输出路径
```

## 八、附录

### 测试用例中的表结构

```mysql
CREATE TABLE `tb_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_name` varchar(100) DEFAULT NULL COMMENT '用户名',
  `login_name` varchar(100) DEFAULT NULL COMMENT '登录名',
  `login_pwd` varchar(100) DEFAULT NULL COMMENT '登录密码',
  `phone` varchar(50) DEFAULT NULL COMMENT '电话号码',
  `role_id` bigint DEFAULT NULL COMMENT '角色id',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
)COMMENT='测试表-用户';
		
CREATE TABLE `tb_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(100) DEFAULT NULL COMMENT '角色名字',
  `role_desc` varchar(100) DEFAULT NULL COMMENT '角色描述',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
)COMMENT='测试表-角色';
		
CREATE TABLE `tb_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_name` varchar(100) DEFAULT NULL COMMENT '菜单名字',
  `path` varchar(100) DEFAULT NULL COMMENT '路径',
  `parent_id` bigint DEFAULT NULL COMMENT '父级id,第一级是0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
)COMMENT='测试表-菜单';
```



### 其它扩展

建议使用db模式，支持大部分场景，如果数据库不支持此加解密算法，可以自己扩展mysql的 udf

下面链接是使用rust扩展sm4 加解密的一个栗子

[encryptor-udf: rust扩展mysql的udf ，本项目简单扩展了sm4的加解密支持](https://gitee.com/tired-of-the-water/encryptor-udf)



### 版本迭代记录

| 版本号 |                           主要内容                           |  日期   |
| :----: | :----------------------------------------------------------: | :-----: |
|  1.0   |                    支持db模式的自动加解密                    | 2024/5  |
| 2.0.0  |            增加pojo模式，pojo同时支持多种算法共存            | 2024/9  |
| 2.1.0  |       将jsqlparser打入项目的jar中，重命名避免版本冲突        | 2025/2  |
| 3.0.0  | 优化项目结构，减少visitor的重复代码，<br>db模式兼容insert(select)密文存储不同的场景 | 2025/3  |
| 3.1.0  |        jsqlparser升级到4.9版本<br />增加脱敏功能支持         | 2025/4  |
| 3.2.0  | 增加 transformation语法自动转换功能<br />（目前支持mysql转达梦） | 2025/6  |
| 3.3.0  |             增加数据隔离支持（maven仓库未发布）              | 2025/7  |
| 3.5.0  |                整体配置方式重构，使用方法变化                | 2025/7  |
| 3.6.0  |               支持大小写敏感配置，简化框架配置               | 2025/11 |

