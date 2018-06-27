/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  // 当parse 完成后，返回 configuration，这个类是核心类，有所有的信息
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  // 解析 xml 配置文件
  // 解析完成后，所有的信息都在 Configuration 对象上
  private void parseConfiguration(XNode root) {
    try {
      /**
       * 解析顺序,所以配置文件的顺序，通常来说也是这样的
       * 1. properties 自定义配置项，resource url 可以设置外置的 properties 文件，也可以在子元素里面设置，配置将用在接下来的解析，也会设置在 configuration 类里面（configuration是核心类，所有的东西最终都在这里）
       * 2. settings 系统配置项，一些开关，全局配置之类的
       * 3. typeAliases
       * 4. plugins
       * 5. objectFactory
       * 6. objectWrapperFactory
       * 7. reflectorFactory
       * 8. environments
       * 9. databaseIdProvider
       * 10. typeHandlers
       * 11. mappers
       */
      //issue #117 read properties first
      // 解析 properties
      /**
       * <properties resource="org/mybatis/example/config.properties">
       *   <property name="username" value="dev_user"/>
       *   <property name="password" value="F2Fa3!33TYyg"/>
       * </properties>
       */
      propertiesElement(root.evalNode("properties"));
      // 解析settings
      /**
       * <settings>
       *   <setting name="cacheEnabled" value="true"/>
       *   <setting name="lazyLoadingEnabled" value="true"/>
       *   <setting name="multipleResultSetsEnabled" value="true"/>
       *   <setting name="useColumnLabel" value="true"/>
       *   <setting name="useGeneratedKeys" value="false"/>
       *   <setting name="autoMappingBehavior" value="PARTIAL"/>
       *   <setting name="autoMappingUnknownColumnBehavior" value="WARNING"/>
       *   <setting name="defaultExecutorType" value="SIMPLE"/>
       *   <setting name="defaultStatementTimeout" value="25"/>
       *   <setting name="defaultFetchSize" value="100"/>
       *   <setting name="safeRowBoundsEnabled" value="false"/>
       *   <setting name="mapUnderscoreToCamelCase" value="false"/>
       *   <setting name="localCacheScope" value="SESSION"/>
       *   <setting name="jdbcTypeForNull" value="OTHER"/>
       *   <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
       * </settings>
       */
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 看看 settings 里面是否设置类 Vfs 实现类（大部分不需要管，只是部署在特殊的容器里面可能需要扩展，VFS 就是在不同容器里面获取资源的方式和路径不同）
      loadCustomVfs(settings);
      // 别名设置，这个经常使用，用简单类名当做全类名的别名
      // 可以直接指定 package   <package name="domain.blog"/>
      // 也可以一个一个指定 <typeAlias alias="Tag" type="domain.blog.Tag"/>
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析插件，插件是用来扩展 mybatis 的地方，比如分页插件等
      // 注册的插件添加到 configuration 上，注意，这里的 property 不能用上面的 properties 里面定义的
      /**
       * <plugins>
       *   <plugin interceptor="org.mybatis.example.ExamplePlugin">
       *     <property name="someProperty" value="100"/>
       *   </plugin>
       * </plugins>
       */
      pluginElement(root.evalNode("plugins"));
      // 解析objectFactory
      // MyBatis 每次创建结果对象的新实例时，它都会使用一个对象工厂（ObjectFactory）实例来完成。
      // 默认的对象工厂需要做的仅仅是实例化目标类，要么通过默认构造方法，要么在参数映射存在的时候通过参数构造方法来实例化。
      // 如果想覆盖对象工厂的默认行为，则可以通过创建自己的对象工厂来实现
      /**
       * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
       *   <property name="someProperty" value="100"/>
       * </objectFactory>
       */
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 objectWrapperFactory
      // 这个类就是用来包装和判断对象是否包装过，默认是没有包装的，后面看看这里有在那里
      /**
       * <objectWrapperFactory type="org..."></objectWrapperFactory>
       */
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 reflectorFactory
      // 就是用来包装 Class 对象，方便反射调用，默认即可
      /**
       * <reflectorFactory type="org..."></reflectorFactory>
       */
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 这里设置 settings ，没有就取默认值
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析 environments
      // 这里配置事物，数据源
      // property 可以直接填直接值，可以使用上面properties 加载的
      // 注意，可以使用多个 environment，用在不同环境，但是只有 default 指定的环境生效
      /**
       * <environments default="development">
       *   <environment id="development">
       *     <transactionManager type="JDBC">
       *       <property name="..." value="..."/>
       *     </transactionManager>
       *     <dataSource type="POOLED">
       *       <property name="driver" value="${driver}"/>
       *       <property name="url" value="${url}"/>
       *       <property name="username" value="${username}"/>
       *       <property name="password" value="${password}"/>
       *     </dataSource>
       *   </environment>
       * </environments>
       */
      environmentsElement(root.evalNode("environments"));
      // 解析 databaseIdProvider
      //MyBatis 可以根据不同的数据库厂商执行不同的语句，这种多厂商的支持是基于映射语句中的 databaseId 属性。
      // MyBatis 会加载不带 databaseId 属性和带有匹配当前数据库 databaseId 属性的所有语句。
      // 如果同时找到带有 databaseId 和不带 databaseId 的相同语句，则后者会被舍弃
      /**
       * <databaseIdProvider type="DB_VENDOR">
       *   <property name="SQL Server" value="sqlserver"/>
       *   <property name="DB2" value="db2"/>
       *   <property name="Oracle" value="oracle" />
       * </databaseIdProvider>
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 类型处理
      // 可以一个一个指定，也可以指定一个package
      // 要有 注解标记 @MappedJdbcTypes(JdbcType.VARCHAR)
      // 这里是 java 类型和 数据库类型转换的类
      /**
       *<typeHandlers>
       *   <package name="org.mybatis.example"/>
       * </typeHandlers>
       *
       * <typeHandlers>
       *   <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
       * </typeHandlers>
       */
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析 mappers
      /**
       * 既然 MyBatis 的行为已经由上述元素配置完了，我们现在就要定义 SQL 映射语句了。但是首先我们需要告诉 MyBatis 到哪里去找到这些语句。
       * Java 在自动查找这方面没有提供一个很好的方法，所以最佳的方式是告诉 MyBatis 到哪里去找映射文件。
       * 你可以使用相对于类路径的资源引用， 或完全限定资源定位符
       */
      /**
       * <!-- 使用相对于类路径的资源引用 -->
       * <mappers>
       *   <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
       *   <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
       *   <mapper resource="org/mybatis/builder/PostMapper.xml"/>
       * </mappers>
       *
       * <!-- 使用完全限定资源定位符（URL） -->
       * <mappers>
       *   <mapper url="file:///var/mappers/AuthorMapper.xml"/>
       *   <mapper url="file:///var/mappers/BlogMapper.xml"/>
       *   <mapper url="file:///var/mappers/PostMapper.xml"/>
       * </mappers>
       *
       * <!-- 使用映射器接口实现类的完全限定类名 -->
       * <mappers>
       *   <mapper class="org.mybatis.builder.AuthorMapper"/>
       *   <mapper class="org.mybatis.builder.BlogMapper"/>
       *   <mapper class="org.mybatis.builder.PostMapper"/>
       * </mappers>
       *
       * <!-- 将包内的映射器接口实现全部注册为映射器 -->
       * <mappers>
       *   <package name="org.mybatis.builder"/>
       * </mappers>
       */
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 加载所有配置项，检查是否有对应的属性，然后返回
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析properties
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          // package 的直接 Mapper
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            // 解析 mapper 的 xml
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            // 同上，不过是根据url获取
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            // 定义的接口，那就直接 addMapper
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
