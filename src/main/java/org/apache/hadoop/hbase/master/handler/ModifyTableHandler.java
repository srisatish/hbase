/**
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.master.handler;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.Server;
import org.apache.hadoop.hbase.catalog.MetaEditor;
import org.apache.hadoop.hbase.master.AssignmentManager;
import org.apache.hadoop.hbase.master.MasterServices;
import org.apache.hadoop.hbase.util.Bytes;

public class ModifyTableHandler extends TableEventHandler {
  private final HTableDescriptor htd;

  public ModifyTableHandler(final byte [] tableName,
      final HTableDescriptor htd, final Server server,
      final MasterServices masterServices) throws IOException {
    super(EventType.C_M_MODIFY_TABLE, tableName, server, masterServices);
    this.htd = htd;
  }

  @Override
  protected void handleTableOperation(List<HRegionInfo> hris)
  throws IOException {
    AssignmentManager am = this.masterServices.getAssignmentManager();
    HTableDescriptor htd = am.getTableDescriptor(Bytes.toString(tableName));
    if (htd == null) {
      throw new IOException("Modify Table operation could not be completed as " +
          "HTableDescritor is missing for table = "
          + Bytes.toString(tableName));
    }
    // Update table descriptor in HDFS

    HTableDescriptor updatedHTD = this.masterServices.getMasterFileSystem()
        .updateTableDescriptor(this.htd);
    // Update in-memory descriptor cache
    am.updateTableDesc(Bytes.toString(tableName), updatedHTD);
  }
  @Override
  public String toString() {
    String name = "UnknownServerName";
    if(server != null && server.getServerName() != null) {
      name = server.getServerName().toString();
    }
    return getClass().getSimpleName() + "-" + name + "-" + getSeqid() + "-" + tableNameStr;
  }
}