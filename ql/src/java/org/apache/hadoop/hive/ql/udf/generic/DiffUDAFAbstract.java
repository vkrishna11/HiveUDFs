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
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.serde2.objectinspector.*;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * abstract class for diff UDAF.
 */
public abstract class DiffUDAFAbstract extends AbstractGenericUDAFResolver {

    static final Log LOG = LogFactory.getLog(GenericUDAFLead.class.getName());

    @Override
    public GenericUDAFEvaluator getEvaluator(GenericUDAFParameterInfo parameters)
            throws SemanticException {

        // Retrieve Input parameters Object Inspectors
        ObjectInspector[] paramOIs = parameters.getParameterObjectInspectors();
        String fNm = functionName(); // retrieve name of the function

        // Atleast one parameter must be passed
        if (!(paramOIs.length >= 1)) {
            throw new UDFArgumentTypeException(paramOIs.length - 1, "Incorrect invocation of " + fNm
                    + ": _FUNC_(<array>)");
        }


        // Validate input parameter data types...
        for (int i = 0; i < paramOIs.length; i++) {
            switch (paramOIs[i].getCategory()) {
                case PRIMITIVE:
                    break;
                case STRUCT:
                    if (i > 0) {
                        throw new UDFArgumentTypeException(0,
                                "Only primitive are accepted as parameter " + i + " but "
                                        + paramOIs[i].getTypeName() + " was passed.");
                    }
                    break;
                case MAP:
                    if (i > 0) {
                        throw new UDFArgumentTypeException(0,
                                "Only primitive are accepted as parameter " + i + " but "
                                        + paramOIs[i].getTypeName() + " was passed.");
                    }
                    break;
                case LIST:
                    if (i > 0) {
                        throw new UDFArgumentTypeException(0,
                                "Only primitive are accepted as parameter " + i + " but "
                                        + paramOIs[i].getTypeName() + " was passed.");
                    }
                    break;
                default:
                    throw new UDFArgumentTypeException(0,
                            "Only primitive, struct, list or map type arguments are accepted but "
                                    + paramOIs[0].getTypeName() + " was passed as parameter 1.");

            }
        }

        // ObjectInspector amtOI = paramOIs[0];


        GenericUDAFDiffAbstractEvaluator eval = createDiffEvaluator();
        // eval.setOffset_amt(1);
        return eval;  // Return GenericUDAFEvaluator
    }

    protected abstract String functionName();

    protected abstract GenericUDAFDiffAbstractEvaluator createDiffEvaluator();

    public static abstract class GenericUDAFDiffAbstractEvaluator extends GenericUDAFEvaluator {

        private transient ObjectInspector[] inputOI;
        // private int offset_amt;
        //private transient Object[] previousObj = null; // to hold previous value, for comparision
        private ArrayList<Object> previousObj = null;
        private String fnName;
        private transient ListObjectInspector listOI;
        private transient PrimitiveObjectInspector primitiveOI;
        private transient StructObjectInspector structOI;
        private transient MapObjectInspector mapOI;
        private int noOfParams;
        private ArrayList<ObjectInspector> objInspectors;
        //boolean restorepls = false;

        public GenericUDAFDiffAbstractEvaluator() {
        }

        /*
         * used to initialize Streaming Evaluator.
         */
        protected GenericUDAFDiffAbstractEvaluator(GenericUDAFDiffAbstractEvaluator src) {
            this.inputOI = src.inputOI;
            // this.offset_amt = 1; // set default
            this.fnName = src.fnName;
            this.mode = src.mode;
            this.previousObj = src.previousObj;
            this.primitiveOI = src.primitiveOI;
            this.listOI = src.listOI;
            this.mapOI = src.mapOI;
            this.structOI = src.structOI;
            this.noOfParams=src.noOfParams;
            this.objInspectors = src.objInspectors;
        }

        public ObjectInspector[] getInputOI() {
            return inputOI;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            this.objInspectors = new ArrayList<>();
            // Diff only requires "MAP" phase, hence accepts only "COMPLETE" mode...
            if (m != Mode.COMPLETE) {
                throw new HiveException("Only COMPLETE mode supported for " + fnName + " function");
            }
            this.noOfParams = parameters.length;
            inputOI = parameters;
            for (int i = 0; i < parameters.length; i++) {
                switch (inputOI[i].getCategory()) {
                    case PRIMITIVE:
                        primitiveOI = (PrimitiveObjectInspector) inputOI[i];
                        objInspectors.add(i,primitiveOI);
                        break;
                    case LIST:
                        listOI = (ListObjectInspector) inputOI[i];
                        objInspectors.add(i,listOI);
                        break;
                    case STRUCT:
                        structOI = (StructObjectInspector) inputOI[i];
                        objInspectors.add(i,structOI);
                        break;
                    case MAP:
                        mapOI = (MapObjectInspector) inputOI[i];
                        objInspectors.add(i,mapOI);
                        break;
                }
            }
            if(inputOI[0].getCategory() != ObjectInspector.Category.PRIMITIVE){
                return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils.
                        getStandardObjectInspector(ObjectInspectorFactory.getStandardListObjectInspector(PrimitiveObjectInspectorFactory.writableStringObjectInspector)));
            }
            else {
                return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils.
                        getStandardObjectInspector(PrimitiveObjectInspectorFactory.writableStringObjectInspector));
            }

            //  return PrimitiveObjectInspectorFactory.writableStringObjectInspector;

            // Set the output result data type same as the first input parameter
           /* return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils
                    .getStandardObjectInspector(parameters[0]));*/
        }

        /*public int getOffset_amt() {
            return this.offset_amt;
        }

        public void setOffset_amt(int offset_amt) {
            this.offset_amt = offset_amt;
        }*/

        public String getFnName() {
            return fnName;
        }

        public void setFnName(String fnName) {
            this.fnName = fnName;
        }

        public int getNoOfParams() {
            return noOfParams;
        }

        protected abstract IDiffBuffer getNewDiffBuffer() throws HiveException;

        @Override
        public AggregationBuffer getNewAggregationBuffer() throws HiveException {
            IDiffBuffer lb = getNewDiffBuffer();
            //  lb.initialize(offset_amt);
            lb.initialize();
            this.previousObj = new ArrayList<>(this.noOfParams);
           // this.restorepls = false;
            return lb;
        }

        @Override
        public void reset(AggregationBuffer agg) throws HiveException {
            //  ((LeadLagBuffer) agg).initialize(offset_amt);
            ((IDiffBuffer) agg).initialize();
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            Object rowExprVal;

            boolean isSame;
            boolean previousExists;
            // int i = 0;
            for (int i = 0; i < parameters.length; i++) {

                rowExprVal = ObjectInspectorUtils.copyToStandardObject(parameters[i], inputOI[i]);

                isSame = true;

                //boolean previousExists;
                switch (inputOI[i].getCategory()) {
                    case PRIMITIVE:

                        //   PrimitiveObjectInspector prim1 = (PrimitiveObjectInspector) inputOI[0];
                        if (((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.STRING) {
                            // retrieve current row as a String
                            String currentStr = (String) ((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveJavaObject(rowExprVal);
                            // If previous row exists, then retrieve it as a String
                            if (!previousObj.isEmpty() && previousObj.size() > i) {

                                String previousStr = (String) ((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveJavaObject(previousObj.get(i));
                                // compare current & previous row...
                                if (!currentStr.equals(previousStr)) {
                                    isSame = false;
                                }
                            }
                        } else if (((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                            // retrieve current row as a Integer
                            Integer currentInt = (Integer) ((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveJavaObject(rowExprVal);

                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                // retrieve previous row as a Integer
                                Integer previousInt = (Integer) ((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveJavaObject(previousObj.get(i));
                                // compare current & previous row...
                                if (!currentInt.equals(previousInt)) {
                                    isSame = false;
                                }
                            }
                        } else if (((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                            // retrieve current row as a Double
                            Double currentDouble = (Double) ((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveJavaObject(rowExprVal);
                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                Double previousDouble = (Double) ((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveJavaObject(previousObj.get(i));
                                // compare current & previous row...
                                if (!currentDouble.equals(previousDouble)) {
                                    isSame = false;
                                }
                            }
                        }

                        break;
                    case STRUCT:
                        // StructObjectInspector struct1 = (StructObjectInspector) inputOI[0];
                        break;
                    case MAP:
                        // MapObjectInspector map1 = (MapObjectInspector) inputOI[0];
                        break;
                    case LIST:

                        //ListObjectInspector list1 = (ListObjectInspector) inputOI[0];
                        PrimitiveObjectInspector elementOI = (PrimitiveObjectInspector) listOI.getListElementObjectInspector();
                        if(elementOI.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.STRING) {
                            // retrieve current & previous rows from the list and compare the values...
                            List<Text> currentList = (List<Text>) listOI.getList(rowExprVal);

                            if (!previousObj.isEmpty() && previousObj.size() > i) {

                                List<Text> previousList = (List<Text>) listOI.getList(previousObj.get(i));
                                for (Text s : currentList) {
                                    if (!s.toString().equals((previousList.get(i)).toString())) {
                                        isSame = false;
                                        break;
                                    }


                                }
                            }
                        }
                        else if(elementOI.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                            List<IntWritable> currentList = (List<IntWritable>) listOI.getList(rowExprVal);

                            if (!previousObj.isEmpty() && previousObj.size() > i) {

                                List<IntWritable> previousList = (List<IntWritable>) listOI.getList(previousObj.get(i));
                                for (IntWritable s : currentList) {
                                    if (!s.equals((previousList.get(i)))) {
                                        isSame = false;
                                        break;
                                    }


                                }
                            }
                        }
                        else if(elementOI.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                            List<DoubleWritable> currentList = (List<DoubleWritable>) listOI.getList(rowExprVal);

                            if (!previousObj.isEmpty() && previousObj.size() > i) {

                                List<DoubleWritable> previousList = (List<DoubleWritable>) listOI.getList(previousObj.get(i));
                                for (DoubleWritable s : currentList) {
                                    if (!s.equals((previousList.get(i)))) {
                                        isSame = false;
                                        break;
                                    }


                                }
                            }
                        }
                        break;
                }



                if (!previousObj.isEmpty() && previousObj.size() > i) {
                    previousExists = true;
                } else {
                    previousExists = false;
                }


                /*if (restorepls) {
                    rowExprVal = previousObj.get(i);
                    restorepls = false;
                } else {
                   // previousObj.set(i,rowExprVal);
                   if(!previousObj.isEmpty()) {
                       previousObj.remove(i);
                   }
                    previousObj.add(i, rowExprVal);
                }*/
                if (!previousObj.isEmpty() && previousObj.size() > i) {
                    //previousObj.remove(i);
                    previousObj.set(i,rowExprVal);
                } else {
                    previousObj.add(i, rowExprVal);
                }


                // if the previous row exists and is the same as the previous row,
                // then set the result value as "null".
                /*if (isSame && previousExists) {
                    rowExprVal = null;
                    restorepls = true;
                }*/


                //  ((IDiffBuffer) agg).addRow(rowExprVal, defaultVal);
                ((IDiffBuffer) agg).addRow(rowExprVal, i, !previousExists ? false : isSame,inputOI[i]);

            }
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            throw new HiveException("terminatePartial not supported");
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            throw new HiveException("merge not supported");
        }

        @Override
        public Object terminate(AggregationBuffer agg) throws HiveException {
            return ((IDiffBuffer) agg).terminate();
        }

    }

}


