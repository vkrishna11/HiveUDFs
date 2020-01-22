package org.apache.hadoop.hive.ql.udf.generic;

/**
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



import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.UDFType;
@Description(
        name = "dedup",
        value = "DEDUP  (<Array>) OVER ([query_partition_clause] order_by_clause); "
                + "The DEDUP function is used to compare data with the previous row.")

@UDFType(impliesOrder = true)
public class VVDedupUDFImp extends VDedupUDFExt {
    @Override
    protected String _getFnName() {
        return "dedup";
    }

    @Override
    protected int getIndex(int amt) {
        return pItr.getIndex() - 1 - amt;
    }

    @Override
    protected Object getRow(int amt) throws HiveException {
        return pItr.lag(amt + 1);
    }

}

