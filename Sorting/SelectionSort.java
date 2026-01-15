package sorting;

import java.util.ArrayList;
import java.util.List;

public class SelectionSort implements StepSortable {

    @Override
    public String name() {
        return "Selection Sort";
    }

    @Override
    public void sort(int[] arr) {
        int n = arr.length;

        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;

            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
            }

            int temp = arr[minIndex];
            arr[minIndex] = arr[i];
            arr[i] = temp;
        }
    }

    @Override
    public List<SortStep> steps(int[] input) {
        int[] arr = input.clone();
        int n = arr.length;
        List<SortStep> steps = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
                steps.add(new SortStep(arr.clone(), j, minIndex, -1, -1));
            }
            int temp = arr[minIndex];
            arr[minIndex] = arr[i];
            arr[i] = temp;
            steps.add(new SortStep(arr.clone(), -1, -1, -1, -1));
        }
        return steps;
    }
}
