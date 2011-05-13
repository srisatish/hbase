package org.apache.hadoop.hbase.regionserver;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.RemoteExceptionHandler;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Writables;
import org.apache.hadoop.util.StringUtils;

import com.google.common.base.Preconditions;

/**
 * Handles processing region splits. Put in a queue, owned by HRegionServer.
 */
class SplitRequest implements Runnable {
  static final Log LOG = LogFactory.getLog(SplitRequest.class);
  private final HRegion parent;
  private final byte[] midKey;
  private final HRegionServer server;

  SplitRequest(HRegion region, byte[] midKey, HRegionServer hrs) {
    Preconditions.checkNotNull(hrs);
    this.parent = region;
    this.midKey = midKey;
    this.server = hrs;
  }

  @Override
  public String toString() {
    return "regionName=" + parent + ", midKey=" + Bytes.toStringBinary(midKey);
  }

  @Override
  public void run() {
    try {
      final long startTime = System.currentTimeMillis();
      SplitTransaction st = new SplitTransaction(parent, midKey);
      // If prepare does not return true, for some reason -- logged inside in
      // the prepare call -- we are not ready to split just now. Just return.
      if (!st.prepare()) return;
      try {
        st.execute(this.server, this.server);
      } catch (Exception e) {
        try {
          LOG.info("Running rollback of failed split of " + parent + "; "
              + e.getMessage());
          st.rollback(this.server, this.server);
          LOG.info("Successful rollback of failed split of " + parent);
        } catch (RuntimeException ee) {
          // If failed rollback, kill server to avoid having a hole in table.
          LOG.info("Failed rollback of failed split of "
              + parent.getRegionNameAsString() + " -- aborting server", ee);
          this.server.abort("Failed split");
        }
        return;
      }
      LOG.info("Region split, META updated, and report to master. Parent="
          + parent.getRegionInfo().getRegionNameAsString() + ", new regions: "
          + st.getFirstDaughter().getRegionNameAsString() + ", "
          + st.getSecondDaughter().getRegionNameAsString() + ". Split took "
          + StringUtils.formatTimeDiff(System.currentTimeMillis(), startTime));
    } catch (IOException ex) {
      LOG.error("Split failed " + this, RemoteExceptionHandler
          .checkIOException(ex));
      server.checkFileSystem();
    }
  }

}