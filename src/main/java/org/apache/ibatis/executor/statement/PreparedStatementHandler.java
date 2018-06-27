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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 这个就是常用的，也是默认的 StatementHandler
 * 对应于 JDBC 的 PreparedStatement
 * 只用看这个就可以了
 * @author Clinton Begin
 */
public class PreparedStatementHandler extends BaseStatementHandler {

  public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  @Override
  public int update(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    int rows = ps.getUpdateCount();
    // 更新操作可能需要获取数据库自动生成的字段值（insert 自增）
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    // 批量操作
    ps.addBatch();
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    // 无论是 更新 还行查询，都执行 execute，然后处理结果集
    ps.execute();
    // 结果集处理类 DefaultResultSetHandler
    return resultSetHandler.<E> handleResultSets(ps);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    // 处理游标
    return resultSetHandler.<E> handleCursorResultSets(ps);
  }

  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    String sql = boundSql.getSql();
    // 这里生成 prepareStatement，对自动生成（自增字段等）的值进行设置
    if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
      String[] keyColumnNames = mappedStatement.getKeyColumns();
      if (keyColumnNames == null) {
        // 如果没有指明生成的字段名，那么就要设置可检索
        return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      } else {
        // 如果设置了字段名，那么直接让JDBC 带出来
        return connection.prepareStatement(sql, keyColumnNames);
      }
    } else if (mappedStatement.getResultSetType() != null) {
      // 设置结果集的滑动方向（单向，双向），设置结果集只读
      return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    } else {
      // 啥都没配置，那就是默认
      return connection.prepareStatement(sql);
    }
  }

  @Override
  public void parameterize(Statement statement) throws SQLException {
    // 设置参数类 DefaultParameterHandler
    parameterHandler.setParameters((PreparedStatement) statement);
  }

}
