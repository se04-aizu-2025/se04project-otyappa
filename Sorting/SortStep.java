package sorting;

import java.util.Arrays;

public class SortStep {
    public final int[] data;
    public final int compareA;
    public final int compareB;
    public final int rangeL;
    public final int rangeR;

    public SortStep(int[] data, int compareA, int compareB, int rangeL, int rangeR) {
        this.data = Arrays.copyOf(data, data.length);
        this.compareA = compareA;
        this.compareB = compareB;
        this.rangeL = rangeL;
        this.rangeR = rangeR;
    }
}
