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



import org.apache.hadoop.hive.ql.exec.ExprNodeEvaluator;
import org.apache.hadoop.hive.ql.exec.PTFPartition.PTFPartitionIterator;
import org.apache.hadoop.hive.ql.exec.PTFUtils;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.serde2.objectinspector.*;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorConverters.Converter;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils.ObjectInspectorCopyOption;

import java.util.List;

public abstract class VDedupUDFExt extends GenericUDF {
    transient ExprNodeEvaluator exprEvaluator;
    transient PTFPartitionIterator<Object> pItr;
    //transient ObjectInspector firstArgOI;
    transient ListObjectInspector firstArgOI;
    transient ObjectInspector defaultArgOI;
    transient Converter defaultValueConverter;
    int amt;

    static {
        PTFUtils.makeTransient(VDedupUDFExt.class, "exprEvaluator", "pItr", "firstArgOI",
                "defaultArgOI", "defaultValueConverter");
    }

    @Override
    public Object evaluate(DeferredObject[] arguments) throws HiveException {
        Object defaultVal = null; // to be changed
        /*if (arguments.length == 3) {
            defaultVal = ObjectInspectorUtils.copyToStandardObject(
                    defaultValueConverter.convert(arguments[2].get()), defaultArgOI);
        }*/

        int idx = pItr.getIndex() - 1;
        int start = 0;
        int end = pItr.getPartition().size();
        try {
            Object ret = null;
            Object ret_lt = null;
            int newIdx = getIndex(amt);

            if (newIdx >= end || newIdx < start) {
                ret = defaultVal;
            } else {
                Object row = getRow(amt);
                ret = exprEvaluator.evaluate(row);
                ret = ObjectInspectorUtils.copyToStandardObject(ret, firstArgOI,
                        ObjectInspectorCopyOption.WRITABLE);

                Object currRow_lt = pItr.resetToIndex(idx);
                // reevaluate expression on current Row, to trigger the Lazy object
                // caches to be reset to the current row.
                ret_lt = exprEvaluator.evaluate(currRow_lt);
                ListObjectInspector ax = (ListObjectInspector) ObjectInspectorUtils.getStandardObjectInspector(firstArgOI);
                List<String> list1 = (List<String>) ax.getList(ret);
                System.out.println("Hello I am here: " + list1);
                List<String> list2 = (List<String>) ax.getList(ret_lt);
                System.out.println("Hello I am here: " + list2);

                if (list1.get(0) == list2.get(0)){
                    ret = null;

                }
                else {

                }
//                if (ret.toString() == ret_lt.toString()) {
//                    ListObjectInspector ax = (ListObjectInspector) ObjectInspectorUtils.getStandardObjectInspector(firstArgOI);
//                    int num1 = ax.getListLength(ret);
//
//                    ret = null;
//                };
            }
            return ret;
        } finally {
            Object currRow = pItr.resetToIndex(idx);
            // reevaluate expression on current Row, to trigger the Lazy object
            // caches to be reset to the current row.
            exprEvaluator.evaluate(currRow);
        }

    }

    @Override
    public ObjectInspector initialize(ObjectInspector[] arguments) throws UDFArgumentException {
        if (!(arguments.length >= 1)) {
            throw new UDFArgumentTypeException(arguments.length - 1, "Incorrect invocation of "
                    + _getFnName() + ": _FUNC_(<array>)");
        }

        amt = 1;
     //   if (arguments.length > 1) {
            ObjectInspector amtOI = arguments[0];
           // if (!ObjectInspectorUtils.isConstantObjectInspector(amtOI)
                    if (amtOI.getCategory() != ObjectInspector.Category.LIST) {
                throw new UDFArgumentTypeException(1, _getFnName() + " First parameter must be an array. " + amtOI.toString());
            }
           // Object o = ((ConstantObjectInspector) amtOI).getWritableConstantValue();
           // amt = ((IntWritable) o).get();
            /*if (amt < 0) {
                throw new UDFArgumentTypeException(1,  " amount can not be nagative. Specified: " + amt);
            }*/
       // }
        // to be reviewed later
        /*if (arguments.length == 3) {
            defaultArgOI = arguments[2];
            ObjectInspectorConverters.getConverter(arguments[2], arguments[0]);
            defaultValueConverter = ObjectInspectorConverters.getConverter(arguments[2], arguments[0]);

        }*/

        firstArgOI = (ListObjectInspector ) arguments[0]; //Array
        /*return ObjectInspectorUtils.getStandardObjectInspector(firstArgOI,
                ObjectInspectorCopyOption.WRITABLE);*/
        //return firstArgOI;
        return (StandardListObjectInspector) ObjectInspectorUtils.getStandardObjectInspector(firstArgOI);
    }

    public ExprNodeEvaluator getExprEvaluator() {
        return exprEvaluator;
    }

    public void setExprEvaluator(ExprNodeEvaluator exprEvaluator) {
        this.exprEvaluator = exprEvaluator;
    }

    public PTFPartitionIterator<Object> getpItr() {
        return pItr;
    }

    public void setpItr(PTFPartitionIterator<Object> pItr) {
        this.pItr = pItr;
    }

    public ListObjectInspector  getFirstArgOI() {
        return firstArgOI;
    }

    public void setFirstArgOI(ListObjectInspector  firstArgOI) {
        this.firstArgOI = firstArgOI;
    }

    public ObjectInspector  getDefaultArgOI() {
        return defaultArgOI;
    }

    public void setDefaultArgOI(ObjectInspector defaultArgOI) {
        this.defaultArgOI = defaultArgOI;
    }

    public Converter getDefaultValueConverter() {
        return defaultValueConverter;
    }

    public void setDefaultValueConverter(Converter defaultValueConverter) {
        this.defaultValueConverter = defaultValueConverter;
    }

    public int getAmt() {
        return amt;
    }

    public void setAmt(int amt) {
        this.amt = amt;
    }

    @Override
    public String getDisplayString(String[] children) {
        assert (children.length == 2);
        return getStandardDisplayString(_getFnName(), children);
    }

    protected abstract String _getFnName();

    protected abstract Object getRow(int amt) throws HiveException;

    protected abstract int getIndex(int amt);

}


