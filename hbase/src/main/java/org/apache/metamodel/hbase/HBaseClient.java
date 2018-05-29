/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.metamodel.hbase;

import java.io.IOException;
import java.util.Set;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.metamodel.MetaModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can perform client-operations on a HBase datastore
 */
public final class HBaseClient {

    private static final Logger logger = LoggerFactory.getLogger(HBaseClient.class);

    private final Connection _connection;

    public HBaseClient(Connection connection) {
        this._connection = connection;
    }

    /**
     * Insert a single row of values to a HBase table.
     * @param tableName
     * @param columns
     * @param values
     * @throws IllegalArgumentException when any parameter is null or the indexOfIdColumn is impossible
     * @throws MetaModelException when no ID-column is found.
     * @throws MetaModelException when a {@link IOException} is catched
     */
    // TODO: Use the ColumnTypes to determine the inserts. Now the toString() method is called on the object.
    public void insertRow(String tableName, HBaseColumn[] columns, Object[] values, int indexOfIdColumn) {
        if (tableName == null || columns == null || values == null || indexOfIdColumn >= values.length
                || values[indexOfIdColumn] == null) {
            throw new IllegalArgumentException(
                    "Can't insert a row without having (correct) tableName, columns, values or indexOfIdColumn");
        }
        try (final Table table = _connection.getTable(TableName.valueOf(tableName))) {
            // Create a put with the values of indexOfIdColumn as rowkey
            final Put put = new Put(Bytes.toBytes(values[indexOfIdColumn].toString()));

            // Add the other values to the put
            for (int i = 0; i < columns.length; i++) {
                if (i != indexOfIdColumn) {
                    put.addColumn(Bytes.toBytes(columns[i].getColumnFamily()), Bytes.toBytes(columns[i].getQualifier()),
                            Bytes.toBytes(values[i].toString()));
                }
            }
            // Add the put to the table
            table.put(put);
        } catch (IOException e) {
            throw new MetaModelException(e);
        }
    }

    /**
     * Delete 1 row based on the key
     * @param tableName
     * @param rowKey
     * @throws IllegalArgumentException when any parameter is null
     * @throws MetaModelException when a {@link IOException} is catched
     */
    public void deleteRow(String tableName, Object rowKey) {
        if (tableName == null || rowKey == null) {
            throw new IllegalArgumentException("Can't delete a row without having tableName or rowKey");
        }
        try (final Table table = _connection.getTable(TableName.valueOf(tableName));) {
            if (rowExists(table, rowKey) == true) {
                table.delete(new Delete(Bytes.toBytes(rowKey.toString())));
            } else {
                logger.warn("Rowkey with value " + rowKey.toString() + " doesn't exist in the table");
            }
        } catch (IOException e) {
            throw new MetaModelException(e);
        }
    }

    /**
     * Checks in the HBase datastore if a row exists based on the key
     * @param table
     * @param rowKey
     * @return boolean
     * @throws IOException
     */
    private boolean rowExists(Table table, Object rowKey) throws IOException {
        final Get get = new Get(Bytes.toBytes(rowKey.toString()));
        return !table.get(get).isEmpty();
    }

    /**
     * Creates a HBase table based on a tableName and it's columnFamilies
     * @param tableName
     * @param columnFamilies
     * @throws IllegalArgumentException when any parameter is null
     * @throws MetaModelException when a {@link IOException} is catched
     */
    public void createTable(String tableName, Set<String> columnFamilies) {
        if (tableName == null || columnFamilies == null || columnFamilies.isEmpty()) {
            throw new IllegalArgumentException("Can't create a table without having the tableName or columnFamilies");
        }
        try (final Admin admin = _connection.getAdmin()) {
            final TableName hBasetableName = TableName.valueOf(tableName);
            final HTableDescriptor tableDescriptor = new HTableDescriptor(hBasetableName);
            // Add all columnFamilies to the tableDescriptor.
            for (final String columnFamilie : columnFamilies) {
                // The ID-column isn't needed because, it will automatically be created.
                if (!columnFamilie.equals(HBaseDataContext.FIELD_ID)) {
                    tableDescriptor.addFamily(new HColumnDescriptor(columnFamilie));
                }
            }
            admin.createTable(tableDescriptor);
        } catch (IOException e) {
            throw new MetaModelException(e);
        }
    }

    /**
     * Disable and drop a table from a HBase datastore
     * @param tableName
     * @throws IllegalArgumentException when tableName is null
     * @throws MetaModelException when a {@link IOException} is catched
     */
    public void dropTable(String tableName) {
        if (tableName == null) {
            throw new IllegalArgumentException("Can't drop a table without having the tableName");
        }
        try (final Admin admin = _connection.getAdmin()) {
            final TableName hBasetableName = TableName.valueOf(tableName);
            admin.disableTable(hBasetableName); // A table must be disabled first, before it can be deleted
            admin.deleteTable(hBasetableName);
        } catch (IOException e) {
            throw new MetaModelException(e);
        }
    }
}
