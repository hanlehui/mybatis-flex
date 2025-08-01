/*
 *  Copyright (c) 2022-2025, Mybatis-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.mybatisflex.core.datasource;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.dialect.DbType;
import com.mybatisflex.core.dialect.DbTypeUtil;
import com.mybatisflex.core.transaction.TransactionContext;
import com.mybatisflex.core.transaction.TransactionalManager;
import com.mybatisflex.core.util.ArrayUtil;
import com.mybatisflex.core.util.StringUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author michael
 */
public class FlexDataSource extends AbstractDataSource {

    private static final char LOAD_BALANCE_KEY_SUFFIX = '*';
    private static final Log log = LogFactory.getLog(FlexDataSource.class);

    private final Map<String, DataSource> dataSourceMap = new HashMap<>();
    private final Map<String, DbType> dbTypeHashMap = new HashMap<>();

    private DbType defaultDbType;
    private String defaultDataSourceKey;
    private DataSource defaultDataSource;

    public FlexDataSource(String dataSourceKey, DataSource dataSource) {
        this(dataSourceKey, dataSource, true);
    }

    public FlexDataSource(String dataSourceKey, DataSource dataSource, boolean needDecryptDataSource) {
        this(dataSourceKey, dataSource, DbTypeUtil.getDbType(dataSource), needDecryptDataSource);
    }

    public FlexDataSource(String dataSourceKey, DataSource dataSource, DbType dbType, boolean needDecryptDataSource) {
        if (needDecryptDataSource) {
            DataSourceManager.decryptDataSource(dataSource);
        }

        // 处理dbType
        dbType = Optional.ofNullable(dbType).orElseGet(() -> DbTypeUtil.getDbType(dataSource));

        this.defaultDataSourceKey = dataSourceKey;
        this.defaultDataSource = dataSource;
        this.defaultDbType = dbType;

        dataSourceMap.put(dataSourceKey, dataSource);
        dbTypeHashMap.put(dataSourceKey, dbType);
    }

    /**
     * 设置默认数据源（提供动态可控性）
     */
    public void setDefaultDataSource(String dataSourceKey) {
        DataSource ds = dataSourceMap.get(dataSourceKey);

        if (Objects.isNull(ds)) {
            throw new IllegalStateException("DataSource not found by key: \"" + dataSourceKey + "\"");
        }

        // 优先取缓存，否则根据数据源返回数据库类型
        DbType dbType = Optional.ofNullable(dbTypeHashMap.get(dataSourceKey)).orElseGet(() -> DbTypeUtil.getDbType(ds));

        this.defaultDataSourceKey = dataSourceKey;
        this.defaultDataSource = ds;
        this.defaultDbType = dbType;
    }

    public void addDataSource(String dataSourceKey, DataSource dataSource) {
        addDataSource(dataSourceKey, dataSource, true);
    }

    public void addDataSource(String dataSourceKey, DataSource dataSource, boolean needDecryptDataSource) {
        addDataSource(dataSourceKey, dataSource, DbTypeUtil.getDbType(dataSource), needDecryptDataSource);
    }

    public void addDataSource(String dataSourceKey, DataSource dataSource, DbType dbType, boolean needDecryptDataSource) {
        if (needDecryptDataSource) {
            DataSourceManager.decryptDataSource(dataSource);
        }

        dbType = Optional.ofNullable(dbType).orElseGet(() -> DbTypeUtil.getDbType(dataSource));

        dataSourceMap.put(dataSourceKey, dataSource);
        dbTypeHashMap.put(dataSourceKey, dbType);
    }


    public void removeDatasource(String dataSourceKey) {
        dataSourceMap.remove(dataSourceKey);
        dbTypeHashMap.remove(dataSourceKey);
    }

    public Map<String, DataSource> getDataSourceMap() {
        return dataSourceMap;
    }

    public Map<String, DbType> getDbTypeHashMap() {
        return dbTypeHashMap;
    }

    public String getDefaultDataSourceKey() {
        return defaultDataSourceKey;
    }

    public DataSource getDefaultDataSource() {
        return defaultDataSource;
    }

    public DbType getDefaultDbType() {
        return defaultDbType;
    }

    public DbType getDbType(String dataSourceKey) {
        return dbTypeHashMap.get(dataSourceKey);
    }


    @Override
    public Connection getConnection() throws SQLException {
        String xid = TransactionContext.getXID();
        if (StringUtil.hasText(xid)) {
            String dataSourceKey = DataSourceKey.get();
            if (StringUtil.noText(dataSourceKey)) {
                dataSourceKey = defaultDataSourceKey;
            }

            Connection connection = TransactionalManager.getConnection(xid, dataSourceKey);
            if (connection == null) {
                connection = proxy(getDataSource().getConnection(), xid);
                TransactionalManager.hold(xid, dataSourceKey, connection);
            }
            return connection;
        } else {
            return getDataSource().getConnection();
        }
    }


    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        String xid = TransactionContext.getXID();
        if (StringUtil.hasText(xid)) {
            String dataSourceKey = DataSourceKey.get();
            if (StringUtil.noText(dataSourceKey)) {
                dataSourceKey = defaultDataSourceKey;
            }
            Connection connection = TransactionalManager.getConnection(xid, dataSourceKey);
            if (connection == null) {
                connection = proxy(getDataSource().getConnection(username, password), xid);
                TransactionalManager.hold(xid, dataSourceKey, connection);
            }
            return connection;
        } else {
            return getDataSource().getConnection(username, password);
        }
    }

    static void closeAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error set autoCommit to false. Cause: " + e);
            }
        }
    }

    static void resetAutoCommit(Connection connection) {
        try {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error resetting autoCommit to true before closing the connection. " +
                    "Cause: " + e);
            }
        }
    }


    public Connection proxy(Connection connection, String xid) {
        return (Connection) Proxy.newProxyInstance(FlexDataSource.class.getClassLoader()
            , new Class[]{Connection.class}
            , new ConnectionHandler(connection, xid)
        );
    }

    /**
     * 方便用于 {@link DbTypeUtil#getDbType(DataSource)}
     */
    public String getUrl() {
        return DbTypeUtil.getJdbcUrl(defaultDataSource);
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return (T) this;
        }
        return getDataSource().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return (iface.isInstance(this) || getDataSource().isWrapperFor(iface));
    }

    /**
     * 获取数据源缺失处理器。
     *
     * @return DataSourceMissingHandler 数据源缺失处理器实例，用于自定义处理逻辑（如：记录日志、抛出异常或提供默认数据源）。
     */
    public DataSourceMissingHandler getDataSourceMissingHandler() {
        return FlexGlobalConfig.getDefaultConfig().getDataSourceMissingHandler();
    }

    protected DataSource getDataSource() {
        DataSource dataSource = defaultDataSource;
        DataSourceMissingHandler dataSourceMissingHandler = getDataSourceMissingHandler();

        if (dataSourceMap.size() > 1) {
            String dataSourceKey = DataSourceKey.get();

            if (StringUtil.hasText(dataSourceKey)) {
                // 负载均衡 key
                if (dataSourceKey.charAt(dataSourceKey.length() - 1) == LOAD_BALANCE_KEY_SUFFIX) {
                    String prefix = dataSourceKey.substring(0, dataSourceKey.length() - 1);
                    List<String> matchedKeys = new ArrayList<>();

                    for (String key : dataSourceMap.keySet()) {
                        if (key.startsWith(prefix)) {
                            matchedKeys.add(key);
                        }
                    }

                    // 当找不到匹配的 key 时，尝试后备匹配
                    if (matchedKeys.isEmpty() && dataSourceMissingHandler != null) {
                        Map<String, DataSource> dsMap = dataSourceMissingHandler.handle(dataSourceKey, dataSourceMap);

                        if (dsMap != null && !dsMap.isEmpty()) {
                            for (String key : dsMap.keySet()) {
                                if (key.startsWith(prefix)) {
                                    matchedKeys.add(key);
                                }
                            }
                        }
                    }

                    if (matchedKeys.isEmpty()) {
                        throw new IllegalStateException("Can not matched dataSource by key: \"" + dataSourceKey + "\"");
                    }

                    String randomKey = matchedKeys.get(ThreadLocalRandom.current().nextInt(matchedKeys.size()));

                    return dataSourceMap.get(randomKey);
                }
                // 非负载均衡 key
                else {
                    dataSource = dataSourceMap.get(dataSourceKey);

                    // 当找不到匹配的 key 时，尝试后备匹配
                    if (dataSource == null && dataSourceMissingHandler != null) {
                        Map<String, DataSource> dsMap = dataSourceMissingHandler.handle(dataSourceKey, dataSourceMap);

                        if (dsMap != null && !dsMap.isEmpty()) {
                            dataSource = dsMap.get(dataSourceKey);
                        }
                    }

                    if (dataSource == null) {
                        throw new IllegalStateException("Cannot get target dataSource by key: \"" + dataSourceKey + "\"");
                    }
                }
            }
        }

        return dataSource;
    }

    private static class ConnectionHandler implements InvocationHandler {
        private static final String[] proxyMethods = new String[]{"commit", "rollback", "close", "setAutoCommit"};
        private final Connection original;
        private final String xid;

        public ConnectionHandler(Connection original, String xid) {
            closeAutoCommit(original);
            this.original = original;
            this.xid = xid;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (ArrayUtil.contains(proxyMethods, method.getName())
                && isTransactional()) {
                // do nothing
                return null;
            }

            // setAutoCommit: true
            if ("close".equalsIgnoreCase(method.getName())) {
                resetAutoCommit(original);
            }

            return method.invoke(original, args);
        }

        private boolean isTransactional() {
            return Objects.equals(xid, TransactionContext.getXID());
        }

    }
}
