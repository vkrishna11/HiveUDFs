package org.apache.hadoop.hive.ql.udf.generic;

import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;

interface IDiffBuffer extends GenericUDAFEvaluator.AggregationBuffer {
    void initialize();

    //  void addRow(Object leadExprValue, Object defaultValue);
    void addRow(Object leadExprValue, int parmNumber, boolean isSameAsPrevious, ObjectInspector inputObject);

    Object terminate();
}
