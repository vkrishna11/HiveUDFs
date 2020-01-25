package org.apache.hadoop.hive.ql.udf.generic;


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
        ArrayList<Object> listOfBooleans;
        ArrayList<Boolean> isSame;
        boolean isFirstValue;
        int parmIndex;


        public void initialize() {

            values = new ArrayList<Object>();
            isSame = new ArrayList<>();
            listOfValues = new ArrayList<>();
            listOfBooleans = new ArrayList<>();
            this.isFirstValue = true;
            parmIndex = 0;
        }


        public void addRow(Object currValue, int parm_Number, boolean isSameAsPrevious, ObjectInspector inputObject) {


            // isSameAsPrevous - identifies if the current value is similar to previous value
            // if yes, then remove the previous element, which has "isSame" set to FALSE
            // in the subsequent lines, the current element with "isSame" set to TRUE will be added to ArrayList

            if (isSameAsPrevious) {
                ArrayList<Object> tempVal = (ArrayList<Object>) listOfValues.get(parm_Number);
                if (!tempVal.isEmpty()) {
                    tempVal.remove(0);
                }
                ArrayList<Object> tempSame = (ArrayList<Object>) listOfBooleans.get(parm_Number);
                if (!tempSame.isEmpty()) {
                    tempSame.remove(0);
                }
            }

            String tempString;
            Text currText;
            ArrayList<Object> resList;

            parmIndex = parm_Number;

            // Convert all into "Text" data type and add to an ArrayList
            switch (inputObject.getCategory()) {
                case PRIMITIVE:
                    if (((PrimitiveObjectInspector) inputObject).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                        tempString = ((IntWritable) currValue).toString();
                        currText = new Text(tempString);

                    } else if (((PrimitiveObjectInspector) inputObject).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                        tempString = ((DoubleWritable) currValue).toString();
                        currText = new Text(tempString);
                    } else {
                        currText = (Text) currValue;
                    }
                    resList = new ArrayList<>();
                    resList.add(currText);
                    currValue = resList;
                    break;
                case LIST:
                    if (((PrimitiveObjectInspector) ((ListObjectInspector) inputObject).getListElementObjectInspector()).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.INT) {
                        tempString = ((IntWritable) currValue).toString();
                        currText = new Text(tempString);


                    } else if (((PrimitiveObjectInspector) ((ListObjectInspector) inputObject).getListElementObjectInspector()).getPrimitiveCategory() == PrimitiveObjectInspector.PrimitiveCategory.DOUBLE) {
                        tempString = ((DoubleWritable) ((ArrayList<Object>) currValue).get(0)).toString();
                        currText = new Text(tempString);

                    } else {
                        tempString = ((Text) ((ArrayList<Object>) currValue).get(0)).toString();
                        currText = new Text(tempString);
                    }
                    resList = new ArrayList<>();
                    resList.add(currText);
                    currValue = resList;
                    break;

            }

            // add values to listofValues and listOfBooleans, which will be read in getNextResult() method.
            try {
                ArrayList<Object> tempVal = (ArrayList<Object>) listOfValues.get(parm_Number);
                ArrayList<Object> tempSame = (ArrayList<Object>) listOfBooleans.get(parm_Number);
                tempVal.add(currValue);
                tempSame.add(isSameAsPrevious);

                listOfValues.set(parm_Number, tempVal);
                listOfBooleans.set(parm_Number, tempSame);
            } catch (IndexOutOfBoundsException ex) {

                //"listofValues" & "listisSame" element in parm_number doesn't exist, create the element
                ArrayList<Object> tempVal = new ArrayList<>();
                tempVal.add(currValue);
                ArrayList<Object> tempSame = new ArrayList<>();
                tempSame.add(isSameAsPrevious);

                listOfValues.add(parm_Number, tempVal);
                listOfBooleans.add(parm_Number, tempSame);

            }
        }

        public Object terminate() {
            // this is just a dummy
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
                        ((ArrayList<Object>) lb.listOfBooleans.get(i)).remove(0);

                    } else {

                        if (!lb.isFirstValue) {
                            if (ObjOI[i].getCategory() == ObjectInspector.Category.LIST) {
                                res = ((ArrayList<Object>) lb.listOfValues.get(i)).get(0);
                            } else {


                                if (ObjOI[i].getCategory() == ObjectInspector.Category.LIST) {
                                    ArrayList<Object> resList = new ArrayList<>();
                                    resList.add(0, ((ArrayList<Object>) lb.listOfValues.get(i)).get(0));
                                    res = resList;
                                } else {
                                    res = ((ArrayList<Object>) lb.listOfValues.get(i)).get(0);
                                }

                            }

                            resIsSame = (Boolean) ((ArrayList<Object>) lb.listOfBooleans.get(i)).get(0);

                            if (resIsSame && res != null) {
                                res = null;
                                ((ArrayList<Object>) lb.listOfBooleans.get(i)).set(0, false);
                            } else {
                                ((ArrayList<Object>) lb.listOfValues.get(i)).remove(0);
                                ((ArrayList<Object>) lb.listOfBooleans.get(i)).remove(0);
                                deleteOthers = true;
                            }


                        } else {

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

