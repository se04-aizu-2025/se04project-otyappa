package sorting;

import java.util.List;

public interface StepSortable extends Sorter {
    List<SortStep> steps(int[] input);
}
