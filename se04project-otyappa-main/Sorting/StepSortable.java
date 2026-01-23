import java.util.List;

public interface StepSortable extends Sorter {
    String name();
    List<SortStep> steps(int[] input);
}
