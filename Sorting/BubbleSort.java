package sorting;

import java.util.ArrayList;
import java.util.List;

public class BubbleSort implements StepSortable {

    @Override
    public String name() {
        return "Bubble Sort";
    }

    @Override
    public void sort(int[] arr) {
        int n = arr.length;

        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
    }

    @Override
    public List<SortStep> steps(int[] input) {
        int[] arr = input.clone();
        int n = arr.length;
        List<SortStep> steps = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
                steps.add(new SortStep(arr.clone(), j, j + 1, -1, -1));
            }
        }
        return steps;
    }
}
