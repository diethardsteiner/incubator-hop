/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * http://www.project-hop.org
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.tableexists;

import org.apache.hop.core.database.Database;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.ITransform;

/**
 * Check if a table exists in a Database *
 *
 * @author Samatar
 * @since 03-Juin-2008
 */

public class TableExists extends BaseTransform implements ITransform {
  private static Class<?> PKG = TableExistsMeta.class; // for i18n purposes, needed by Translator!!

  private TableExistsMeta meta;
  private TableExistsData data;

  public TableExists( TransformMeta transformMeta, ITransformData data, int copyNr, PipelineMeta pipelineMeta,
                      Pipeline pipeline ) {
    super( transformMeta, meta, data, copyNr, pipelineMeta, pipeline );
  }

  public boolean processRow() throws HopException {
    meta = (TableExistsMeta) smi;
    data = (TableExistsData) sdi;

    boolean sendToErrorRow = false;
    String errorMessage = null;

    Object[] r = getRow(); // Get row from input rowset & set row busy!
    if ( r == null ) { // no more input to be expected...

      setOutputDone();
      return false;
    }

    boolean tablexists = false;
    try {
      if ( first ) {
        first = false;
        data.outputRowMeta = getInputRowMeta().clone();
        meta.getFields( data.outputRowMeta, getTransformName(), null, null, this, metaStore );

        // Check is tablename field is provided
        if ( Utils.isEmpty( meta.getDynamicTablenameField() ) ) {
          logError( BaseMessages.getString( PKG, "TableExists.Error.TablenameFieldMissing" ) );
          throw new HopException( BaseMessages.getString( PKG, "TableExists.Error.TablenameFieldMissing" ) );
        }

        // cache the position of the field
        if ( data.indexOfTablename < 0 ) {
          data.indexOfTablename = getInputRowMeta().indexOfValue( meta.getDynamicTablenameField() );
          if ( data.indexOfTablename < 0 ) {
            // The field is unreachable !
            logError( BaseMessages.getString( PKG, "TableExists.Exception.CouldnotFindField" )
              + "[" + meta.getDynamicTablenameField() + "]" );
            throw new HopException( BaseMessages.getString(
              PKG, "TableExists.Exception.CouldnotFindField", meta.getDynamicTablenameField() ) );
          }
        }
      } // End If first

      // get tablename
      String tablename = getInputRowMeta().getString( r, data.indexOfTablename );

      // Check if table exists on the specified connection
      tablexists = data.db.checkTableExists( data.realSchemaname, tablename );

      Object[] outputRowData = RowDataUtil.addValueData( r, getInputRowMeta().size(), tablexists );

      // add new values to the row.
      putRow( data.outputRowMeta, outputRowData ); // copy row to output rowset(s);

      if ( log.isRowLevel() ) {
        logRowlevel( BaseMessages.getString( PKG, "TableExists.LineNumber", getLinesRead()
          + " : " + getInputRowMeta().getString( r ) ) );
      }
    } catch ( HopException e ) {
      if ( getTransformMeta().isDoingErrorHandling() ) {
        sendToErrorRow = true;
        errorMessage = e.toString();
      } else {
        logError( BaseMessages.getString( PKG, "TableExists.ErrorInTransformRunning" + " : " + e.getMessage() ) );
        throw new HopTransformException( BaseMessages.getString( PKG, "TableExists.Log.ErrorInTransform" ), e );
      }
      if ( sendToErrorRow ) {
        // Simply add this row to the error row
        putError( getInputRowMeta(), r, 1, errorMessage, meta.getResultFieldName(), "TableExistsO01" );
      }
    }

    return true;
  }

  public boolean init() {
    meta = (TableExistsMeta) smi;
    data = (TableExistsData) sdi;

    if ( super.init() ) {
      if ( Utils.isEmpty( meta.getResultFieldName() ) ) {
        logError( BaseMessages.getString( PKG, "TableExists.Error.ResultFieldMissing" ) );
        return false;
      }

      data.db = new Database( this, meta.getDatabase() );
      data.db.shareVariablesWith( this );
      if ( !Utils.isEmpty( meta.getSchemaname() ) ) {
        data.realSchemaname = environmentSubstitute( meta.getSchemaname() );
      }

      try {
        if ( getPipelineMeta().isUsingUniqueConnections() ) {
          synchronized ( getPipeline() ) {
            data.db.connect( getPipeline().getTransactionId(), getPartitionID() );
          }
        } else {
          data.db.connect( getPartitionID() );
        }

        if ( log.isDetailed() ) {
          logDetailed( BaseMessages.getString( PKG, "TableExists.Log.ConnectedToDB" ) );
        }

        return true;
      } catch ( HopException e ) {
        logError( BaseMessages.getString( PKG, "TableExists.Log.DBException" ) + e.getMessage() );
        if ( data.db != null ) {
          data.db.disconnect();
        }
      }
    }
    return false;
  }

  public void.dispose() {
    meta = (TableExistsMeta) smi;
    data = (TableExistsData) sdi;
    if ( data.db != null ) {
      data.db.disconnect();
    }
    super.dispose();
  }
}