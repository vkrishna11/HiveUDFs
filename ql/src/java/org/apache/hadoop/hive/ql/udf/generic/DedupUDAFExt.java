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



import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.serde2.objectinspector.*;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.Text;

import java.util.List;

/**
 * abstract class for Lead & lag UDAFs GenericUDAFLeadLag.
 *
 */
public abstract class DedupUDAFExt extends AbstractGenericUDAFResolver {

    static final Log LOG = LogFactory.getLog(GenericUDAFLead.class.getName());

    @Override
    public GenericUDAFEvaluator getEvaluator(GenericUDAFParameterInfo parameters)
            throws SemanticException {

        ObjectInspector[] paramOIs = parameters.getParameterObjectInspectors();
        String fNm = functionName();

        if (!(paramOIs.length >= 1)) {
            throw new UDFArgumentTypeException(paramOIs.length - 1, "Incorrect invocation of " + fNm
                    + ": _FUNC_(<array>)");
        }

        int amt = 1;
        //int count = paramOIs.length;
       // count--;
        for(int i=0;i<paramOIs.length;i++) {
            switch (paramOIs[i].getCategory()) {
                case PRIMITIVE:
                case STRUCT:
                case MAP:

                case LIST:
                    break;
                default:
                    throw new UDFArgumentTypeException(0,
                            "Only primitive, struct, list or map type arguments are accepted but "
                                    + paramOIs[0].getTypeName() + " was passed as parameter 1.");
            }
        }
    //    if (paramOIs.length > 1) {
            // ObjectInspector amtOI = paramOIs[1];
            ObjectInspector amtOI = paramOIs[0];

            //if (!ObjectInspectorUtils.isConstantObjectInspector(amtOI)
           /*     if (amtOI.getCategory() != ObjectInspector.Category.LIST) {
                throw new UDFArgumentTypeException(1, fNm + " first parameter must be an array"
                        + " " + amtOI.getCategory() + " from vamshi's vault");
            }*/
           /* Object o = ((ConstantObjectInspector) amtOI).getWritableConstantValue();
            amt = ((IntWritable) o).get();
            if (amt < 0) {
                throw new UDFArgumentTypeException(1, fNm + " amount can not be vamshinagative. Specified: " + amt );
            }*/
     //   }

       /* if (paramOIs.length == 3) {
            ObjectInspectorConverters.getConverter(paramOIs[2], paramOIs[0]);
        }*/

        GenericUDAFDedupEvaluator eval = createLLEvaluator();
        eval.setAmt(amt);
        return eval;
    }

    protected abstract String functionName();

    protected abstract GenericUDAFDedupEvaluator createLLEvaluator();

    public static abstract class GenericUDAFDedupEvaluator extends GenericUDAFEvaluator {

        private transient ObjectInspector[] inputOI;
        private int amt;
        Object saveval;
        String fnName;
        // private transient Converter defaultValueConverter;

        public GenericUDAFDedupEvaluator() {
        }

        /*
         * used to initialize Streaming Evaluator.
         */
        protected GenericUDAFDedupEvaluator(GenericUDAFDedupEvaluator src) {
            this.inputOI = src.inputOI;
            this.amt = src.amt;
            this.fnName = src.fnName;
           // this.defaultValueConverter = src.defaultValueConverter;
            this.mode = src.mode;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (m != Mode.COMPLETE) {
                throw new HiveException("Only COMPLETE mode supported for " + fnName + " function");
            }

            inputOI = parameters;

           /* if (parameters.length == 3) {
                defaultValueConverter = ObjectInspectorConverters
                        .getConverter(parameters[2], parameters[0]);
            }*/
          /* return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils.
             getStandardObjectInspector(PrimitiveObjectInspectorFactory.writableStringObjectInspector));*/
          //  return PrimitiveObjectInspectorFactory.writableStringObjectInspector;

            return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils
                    .getStandardObjectInspector(parameters[0]));
        }

        public int getAmt() {
            return amt;
        }

        public void setAmt(int amt) {
            this.amt = amt;
        }

        public String getFnName() {
            return fnName;
        }

        public void setFnName(String fnName) {
            this.fnName = fnName;
        }

        protected abstract LeadLagBuffer getNewLLBuffer() throws HiveException;

        @Override
        public AggregationBuffer getNewAggregationBuffer() throws HiveException {
            LeadLagBuffer lb = getNewLLBuffer();
            lb.initialize(amt);
            saveval=null;
            return lb;
        }

        @Override
        public void reset(AggregationBuffer agg) throws HiveException {
            ((LeadLagBuffer) agg).initialize(amt);
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            Object rowExprVal = ObjectInspectorUtils.copyToStandardObject(parameters[0], inputOI[0]);
            /*Object defaultVal = parameters.length > 2 ? ObjectInspectorUtils.copyToStandardObject(
                    defaultValueConverter.convert(parameters[2]), inputOI[0]) : null;*/
           //Object defaultVal = null;
            boolean matches = true;
            boolean first;
            int i = 0;
            switch (inputOI[0].getCategory()) {
                case PRIMITIVE:

                    PrimitiveObjectInspector prim1 = (PrimitiveObjectInspector) inputOI[0];
                    if (prim1.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.STRING) {
                     String Primstring1 = (String) prim1.getPrimitiveJavaObject(rowExprVal);
                        if (saveval != null) {
                            String Primstring2 = (String) prim1.getPrimitiveJavaObject(saveval);

                            if (!Primstring1.equals(Primstring2)) {
                                matches = false;
                            }
                        }
                }
                    if (prim1.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                        Integer Primint1 = (Integer) prim1.getPrimitiveJavaObject(rowExprVal);
                        if (saveval != null) {
                            Integer Primint2 = (Integer) prim1.getPrimitiveJavaObject(saveval);

                            if (!Primint1.equals(Primint2)) {
                                matches = false;
                            }
                        }
                    }
                    if (prim1.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                        Double Primdouble1 = (Double) prim1.getPrimitiveJavaObject(rowExprVal);
                        if (saveval != null) {
                            Double Primdouble2 = (Double) prim1.getPrimitiveJavaObject(saveval);

                            if (!Primdouble1.equals(Primdouble2)) {
                                matches = false;
                            }
                        }
                    }

                    break;
                case STRUCT:
                    StructObjectInspector struct1 = (StructObjectInspector) inputOI[0];
                    break;
                case MAP:
                    MapObjectInspector map1 = (MapObjectInspector) inputOI[0];
                    break;
                case LIST:

                    ListObjectInspector list1 = (ListObjectInspector) inputOI[0];
                    List<Text> list_val1 = (List<Text>) list1.getList(rowExprVal);

                    if (saveval != null) {
                        List<Text> list_val2 = (List<Text>) list1.getList(saveval);
                        for (Text s : list_val1) {
                            String abc = s.toString();
                            String mno = (list_val2.get(i)).toString();
                          //  for (String q : list_val2) {
                                if (!abc.equals(mno)) {
                                 matches = false;
                                 break;
                                }
                                i++;

                          //  }
                        }
                    }
                        break;
            }
            System.err.println("Im in the iterate function");
          //  if (String.valueOf(saveval) != ((Integer) inputOI[0]).getPrimitiveJavaObject(rowExprVal)){
                Object defaultVal = rowExprVal;
                if (saveval != null) {
                    first = true;
                } else
                {
                    first = false;
                }
                saveval = rowExprVal;
                if (matches == true && first){
                    rowExprVal = null;
                }
          //  }



            ((LeadLagBuffer) agg).addRow(rowExprVal, defaultVal);


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
            return ((LeadLagBuffer) agg).terminate();
        }

    }

}


