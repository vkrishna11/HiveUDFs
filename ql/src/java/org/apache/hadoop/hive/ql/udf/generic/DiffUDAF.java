package org.apache.hadoop.hive.ql.udf.generic;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.WindowFunctionDescription;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.plan.ptf.WindowFrameDef;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;

import java.util.ArrayList;

@WindowFunctionDescription
        (
                description = @Description(
                        name = "diff",
                        value = "_FUNC_(<array>)"
                ),
                supportsWindow = false,
                pivotResult = true,
                impliesOrder = true
        )
public class DiffUDAF extends DiffUDAFAbstract {

    static final Log LOG = LogFactory.getLog(DiffUDAF.class.getName());


    @Override
    protected String functionName() {
        return "Diff";
    }

    @Override
    protected GenericUDAFDiffAbstractEvaluator createDiffEvaluator() {
        return new GenericUDAFDiffEvaluator();
    }

    public static class GenericUDAFDiffEvaluator extends GenericUDAFDiffAbstractEvaluator {

        public GenericUDAFDiffEvaluator() {
        }

        /*
         * used to initialize Streaming Evaluator.
         */
        protected GenericUDAFDiffEvaluator(GenericUDAFDiffAbstractEvaluator src) {
            super(src);
        }

        @Override
        protected IDiffBuffer getNewDiffBuffer() throws HiveException {
            return new DiffBuffer();
        }

        @Override
        public GenericUDAFEvaluator getWindowingEvaluator(WindowFrameDef wFrmDef) {

            return new GenericUDAFDiffExtEvaluatorStreaming(this);
        }

    }

    static class DiffBuffer implements IDiffBuffer {
        ArrayList<Object> values;
        ArrayList<Object> listOfValues;
        ArrayList<Object> listisSame;
        ArrayList<Boolean> isSame;


        boolean isFirstValue;


        int parmIndex;


        public void initialize() {

            values = new ArrayList<Object>();
            isSame = new ArrayList<>();
            listOfValues = new ArrayList<>();
            listisSame = new ArrayList<>();

            this.isFirstValue = true;
            parmIndex = 0;
        }


        public void addRow(Object currValue, int parm_Number, boolean isSameAsPrevious, ObjectInspector inputObject) {


            if (isSameAsPrevious) {

                if (parm_Number == 0) {
                    values.remove(parm_Number);
                    isSame.remove(parm_Number);
                    // listOfValues.set(parm_Number, values);
                    //listisSame.set(parm_Number, isSame);
                } else {

                    ArrayList<Object> tempVal = (ArrayList<Object>) listOfValues.get(parm_Number);
                    tempVal.set(0, null);
                    ArrayList<Object> tempSame = (ArrayList<Object>) listisSame.get(parm_Number);
                    tempSame.set(0, false);
                }


            }
            String tempString;
            Text currText;
            ArrayList<Object> resList;

            parmIndex = parm_Number;
            switch (inputObject.getCategory()) {
                case PRIMITIVE:
                    if (((PrimitiveObjectInspector) inputObject).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                        tempString = ((IntWritable) currValue).toString();
                        currText = new Text(tempString);
                        currValue = currText;
                    } else if (((PrimitiveObjectInspector) inputObject).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                        tempString = ((DoubleWritable) currValue).toString();
                        currText = new Text(tempString);
                        currValue = currText;
                    }
                    break;
                case LIST:
                    if (((PrimitiveObjectInspector) ((ListObjectInspector) inputObject).getListElementObjectInspector()).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                        tempString = ((IntWritable) currValue).toString();
                        currText = new Text(tempString);
                        resList = new ArrayList<>();
                        resList.add(currText);
                        currValue = resList;

                    } else if (((PrimitiveObjectInspector) ((ListObjectInspector) inputObject).getListElementObjectInspector()).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                        tempString = ((DoubleWritable) ((ArrayList<Object>) currValue).get(0)).toString();
                        currText = new Text(tempString);
                        resList = new ArrayList<>();
                        resList.add(currText);
                        currValue = resList;
                    }
                    break;

            }


            if (listOfValues.isEmpty() && parm_Number == 0) {
                values.add(currValue);
                isSame.add(isSameAsPrevious);
                listOfValues.add(parm_Number, values);
                listisSame.add(parm_Number, isSame);
            } else if (!listOfValues.isEmpty() && parm_Number == 0) {
                values.add(currValue);
                isSame.add(isSameAsPrevious);
                listOfValues.set(parm_Number, values);
                listisSame.set(parm_Number, isSame);
            } else if (listOfValues.isEmpty() && parm_Number > 0) {
                ArrayList<Object> tempVal = new ArrayList<>();
                tempVal.add(currValue);
                ArrayList<Object> tempSame = new ArrayList<>();
                tempSame.add(isSameAsPrevious);

                listOfValues.add(parm_Number, tempVal);
                listisSame.add(parm_Number, tempSame);
            } else if (!listOfValues.isEmpty() && parm_Number > 0) {
                try {
                    ArrayList<Object> tempVal = (ArrayList<Object>) listOfValues.get(parm_Number);
                    tempVal.add(currValue);
                    ArrayList<Object> tempSame = (ArrayList<Object>) listisSame.get(parm_Number);
                    tempSame.add(isSameAsPrevious);

                    listOfValues.set(parm_Number, tempVal);
                    listisSame.set(parm_Number, tempSame);
                } catch (IndexOutOfBoundsException ex) {
                    ArrayList<Object> tempVal = new ArrayList<>();
                    tempVal.add(currValue);
                    ArrayList<Object> tempSame = new ArrayList<>();
                    tempSame.add(isSameAsPrevious);

                    listOfValues.add(parm_Number, tempVal);
                    listisSame.add(parm_Number, tempSame);
                }
            }


        }

        public Object terminate() {


            return values;
        }
    }

    /*
     * StreamingEval: wrap regular eval. on getNext remove first row from values
     * and return it.
     */
    static class GenericUDAFDiffExtEvaluatorStreaming extends GenericUDAFDiffEvaluator
            implements ISupportStreamingModeForWindowing {

        protected GenericUDAFDiffExtEvaluatorStreaming(GenericUDAFDiffAbstractEvaluator src) {
            super(src);
        }

        @Override
        public Object getNextResult(AggregationBuffer agg) throws HiveException {

            DiffBuffer lb = (DiffBuffer) agg;

            Object res = null;
            boolean resIsSame;
            boolean deleteOthers = false;

            ObjectInspector[] ObjOI = super.getInputOI();

            if (!lb.listOfValues.isEmpty()) {
                for (int i = 0; i <= lb.parmIndex; i++) {
                    if (deleteOthers) {

                        ((ArrayList<Object>) lb.listOfValues.get(i)).remove(0);
                        ((ArrayList<Object>) lb.listisSame.get(i)).remove(0);

                    } else {

                        if (!lb.isFirstValue) {
                            if (i == 0) {
                                res = ((ArrayList<Object>) lb.listOfValues.get(i)).get(0);
                            } else {

                                // resList.add(((ArrayList<Object>) lb.listOfValues.get(i)).get(0));
                                // ObjOI = super.getInputOI();
                                if (ObjOI[0].getCategory() == ObjectInspector.Category.LIST) {
                                    ArrayList<Object> resList = new ArrayList<>();
                                    resList.add(0, ((ArrayList<Object>) lb.listOfValues.get(i)).get(0));
                                    res = resList;
                                } else {
                                    res = ((ArrayList<Object>) lb.listOfValues.get(i)).get(0);
                                }

                            }

                            resIsSame = (Boolean) ((ArrayList<Object>) lb.listisSame.get(i)).get(0);

                            if ((ObjOI[0].getCategory() == ObjectInspector.Category.LIST) && (((ArrayList<Object>) res).get(0) == null)
                                    || ((ObjOI[0].getCategory() == ObjectInspector.Category.PRIMITIVE) && res == null)) {
                                ((ArrayList<Object>) lb.listOfValues.get(i)).remove(0);
                                ((ArrayList<Object>) lb.listisSame.get(i)).remove(0);
                                continue;

                        } else if (resIsSame && res != null) {
                            res = null;
                            ((ArrayList<Object>) lb.listisSame.get(i)).set(0, false);
                        } else {
                            ((ArrayList<Object>) lb.listOfValues.get(i)).remove(0);
                            ((ArrayList<Object>) lb.listisSame.get(i)).remove(0);
                            deleteOthers = true;
                        }


                    } else{

                        res = ((ArrayList<Object>) lb.listOfValues.get(i)).get(0);

                        break;
                    }

                }
            }
            lb.isFirstValue = false;

            if (res == null) {
                return ISupportStreamingModeForWindowing.NULL_RESULT;
            }

            return res;
        }


            return null;
    }

    @Override
    public int getRowsRemainingAfterTerminate() throws HiveException {

        return 0;
    }
}

}

