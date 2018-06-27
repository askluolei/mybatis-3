/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * sql 执行器
 * 这里是sql执行的地方
 * 这里是内部执行器，里面的对象都是内部封装的
 * @author Clinton Begin
 */
public interface Executor {

  ResultHandler NO_RESULT_HANDLER = null;

  /**
   * 更新操作，就只用 sql，加上参数就行了
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  int update(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 一般来说，只用看参数最长的方法就行了
   * @param ms 这个是sql定义的地方
   * @param parameter 传递的参数
   * @param rowBounds 分页信息
   * @param resultHandler 结果集处理
   * @param cacheKey 缓存key
   * @param boundSql sql，处理了动态内容后的
   * @return 返回结果集合
   * @throws SQLException
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  /**
   * 这个返回的是游标
   * @param ms
   * @param parameter
   * @param rowBounds
   * @return
   * @throws SQLException
   */
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  /**
   * 批量执行，flush 缓存的指令
   * @return
   * @throws SQLException
   */
  List<BatchResult> flushStatements() throws SQLException;

  /**
   * 提交事务
   * @param required
   * @throws SQLException
   */
  void commit(boolean required) throws SQLException;

  /**
   * 回滚
   * @param required
   * @throws SQLException
   */
  void rollback(boolean required) throws SQLException;

  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  boolean isCached(MappedStatement ms, CacheKey key);

  void clearLocalCache();

  /**
   * 延时加载
   * @param ms
   * @param resultObject
   * @param property
   * @param key
   * @param targetType
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  /**
   * 获取事务管理类
   * @return
   */
  Transaction getTransaction();

  void close(boolean forceRollback);

  boolean isClosed();

  /**
   * 设置包装执行器
   * @param executor
   */
  void setExecutorWrapper(Executor executor);

}
