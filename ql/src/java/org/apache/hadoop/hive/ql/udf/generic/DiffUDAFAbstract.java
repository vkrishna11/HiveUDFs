package org.apache.hadoop.hive.ql.udf.generic;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.serde2.objectinspector.*;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

import java.util.ArrayList;

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

                    break;
                case MAP:

                    break;
                case LIST:

                    break;
                default:
                    throw new UDFArgumentTypeException(0,
                            "Only primitive, struct, list or map type arguments are accepted but "
                                    + paramOIs[0].getTypeName() + " was passed as parameter 1.");

            }
        }

        GenericUDAFDiffAbstractEvaluator eval = createDiffEvaluator();

        return eval;  // Return GenericUDAFEvaluator instance
    }

    protected abstract String functionName();

    protected abstract GenericUDAFDiffAbstractEvaluator createDiffEvaluator();

    public static abstract class GenericUDAFDiffAbstractEvaluator extends GenericUDAFEvaluator {

        private transient ObjectInspector[] inputOI;
        private ArrayList<Object> previousObj = null; // ArrayList to hold previous values...
        private String fnName;
        private transient ListObjectInspector listOI;
        private transient PrimitiveObjectInspector primitiveOI;
        private transient StructObjectInspector structOI;
        private transient MapObjectInspector mapOI;
        private transient PrimitiveObjectInspector elementOI;
        private int noOfParams;
        private ArrayList<ObjectInspector> objInspectors;  // ArrayList to hold Object Inspectors


        public GenericUDAFDiffAbstractEvaluator() {
        }

        /*
         * used to initialize Streaming Evaluator.
         */
        protected GenericUDAFDiffAbstractEvaluator(GenericUDAFDiffAbstractEvaluator src) {
            this.inputOI = src.inputOI;
            this.fnName = src.fnName;
            this.mode = src.mode;
            this.previousObj = src.previousObj;
            this.primitiveOI = src.primitiveOI;
            this.listOI = src.listOI;
            this.mapOI = src.mapOI;
            this.structOI = src.structOI;
            this.noOfParams = src.noOfParams;
            this.objInspectors = src.objInspectors;
        }

        public ObjectInspector[] getInputOI() {
            return inputOI;
        }

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {

            super.init(m, parameters);
            this.objInspectors = new ArrayList<>(); // Initialize ObjectInspector ArrayList

            // Diff only requires "MAP" phase, hence accepts only "COMPLETE" mode...
            if (m != Mode.COMPLETE) {
                throw new HiveException("Only COMPLETE mode supported for " + fnName + " function");
            }

            this.noOfParams = parameters.length;
            inputOI = parameters;

            // Loop through all the arguments & populate ObjectInspector ArrayList...
            for (int i = 0; i < parameters.length; i++) {
                switch (inputOI[i].getCategory()) {
                    case PRIMITIVE:
                        primitiveOI = (PrimitiveObjectInspector) inputOI[i];
                        objInspectors.add(i, primitiveOI);
                        break;
                    case LIST:
                        listOI = (ListObjectInspector) inputOI[i];
                        objInspectors.add(i, listOI);
                        break;
                    case STRUCT:
                        structOI = (StructObjectInspector) inputOI[i];
                        objInspectors.add(i, structOI);
                        break;
                    case MAP:
                        mapOI = (MapObjectInspector) inputOI[i];
                        objInspectors.add(i, mapOI);
                        break;
                }
            }

            // Output data type must be  "ArrayList<String>" for all PRIMITIVE input argument (first argument)...
            // regardless of Double, Integer etc.
            // ArrayList is mandatory as PivotResult=TRUE

           /* if (inputOI[0].getCategory() != ObjectInspector.Category.PRIMITIVE) {
                return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils.
                        getStandardObjectInspector(ObjectInspectorFactory.getStandardListObjectInspector(PrimitiveObjectInspectorFactory.writableStringObjectInspector)));
            } else {

                // and "ArrayList<ArrayList<String>>" for LIST input parameter (first argument)...
                // regardless of ArrayList<Double>, ArrayList<Integer> etc...
                // Outer ArrayList is mandatory as PivotResult=TRUE

                return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils.
                        getStandardObjectInspector(PrimitiveObjectInspectorFactory.writableStringObjectInspector));
            }*/
            return ObjectInspectorFactory.getStandardListObjectInspector(ObjectInspectorUtils.
                    getStandardObjectInspector(ObjectInspectorFactory.getStandardListObjectInspector(PrimitiveObjectInspectorFactory.writableStringObjectInspector)));

        }


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
            lb.initialize();
            // initialize previousObj ArrayList
            this.previousObj = new ArrayList<>(this.noOfParams);

            return lb;
        }

        @Override
        public void reset(AggregationBuffer agg) throws HiveException {

            ((IDiffBuffer) agg).initialize();
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            Object rowExprVal;

            boolean isSame; // identifies if the current row is same as previous row
            boolean previousExists;

            for (int i = 0; i < parameters.length; i++) {

                rowExprVal = ObjectInspectorUtils.copyToStandardObject(parameters[i], inputOI[i]);

                isSame = true;

                // Read one by one arguments in a loop, using respective Object Inspectors
                switch (inputOI[i].getCategory()) {
                    case PRIMITIVE:
                        // For String...
                        if (((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.STRING) {

                            // compare current object to previous object
                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                if (ObjectInspectorUtils.compare(rowExprVal, inputOI[i], previousObj.get(i), inputOI[i]) != 0) {
                                    isSame = false;
                                }

                            }
                            // For Integer...
                        } else if (((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {

                            // compare current object to previous object
                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                if (ObjectInspectorUtils.compare(rowExprVal, inputOI[i], previousObj.get(i), inputOI[i]) != 0) {
                                    isSame = false;
                                }

                            }
                            // For Double
                        } else if (((PrimitiveObjectInspector) objInspectors.get(i)).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                            // compare current object to previous object
                            if (!previousObj.isEmpty() && previousObj.size() > i) {

                                if (ObjectInspectorUtils.compare(rowExprVal, inputOI[i], previousObj.get(i), inputOI[i]) != 0) {
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

                        elementOI = (PrimitiveObjectInspector) (((ListObjectInspector) objInspectors.get(i)).getListElementObjectInspector());

                        // For String...
                        if (elementOI.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.STRING) {

                            // compare current object to previous object
                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                if (ObjectInspectorUtils.compare(rowExprVal, inputOI[i], previousObj.get(i), inputOI[i]) != 0) {
                                    isSame = false;
                                }

                            }
                        }
                        // For Integer...
                        else if (elementOI.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {

                            // compare current object to previous object
                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                if (ObjectInspectorUtils.compare(rowExprVal, inputOI[i], previousObj.get(i), inputOI[i]) != 0) {
                                    isSame = false;
                                }

                            }
                        }
                        // For Double
                        else if (elementOI.getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {

                            // compare current object to previous object
                            if (!previousObj.isEmpty() && previousObj.size() > i) {
                                if (ObjectInspectorUtils.compare(rowExprVal, inputOI[i], previousObj.get(i), inputOI[i]) != 0) {
                                    isSame = false;
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


                // store current value into previous value
                if (!previousObj.isEmpty() && previousObj.size() > i) {

                    previousObj.set(i, rowExprVal);
                } else {
                    previousObj.add(i, rowExprVal);
                }

                ((IDiffBuffer) agg).addRow(rowExprVal, i, !(previousExists) ? false : isSame, inputOI[i]);

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


