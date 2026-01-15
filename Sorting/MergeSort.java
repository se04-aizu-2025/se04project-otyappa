package sorting;

import java.util.ArrayList;
import java.util.List;

public class MergeSort implements StepSortable {

    @Override
    public String name() {
        return "Merge Sort";
    }

    @Override
    public void sort(int[] arr) {
        if (arr.length <= 1) return;
        mergeSortRecursive(arr, 0, arr.length - 1);
    }

    @Override
    public List<SortStep> steps(int[] input) {
        int[] arr = input.clone();
        List<SortStep> steps = new ArrayList<>();
        if (arr.length <= 1) return steps;
        int[] temp = new int[arr.length];
        mergeSortWithSteps(arr, temp, 0, arr.length - 1, steps);
        return steps;
    }

    private void mergeSortRecursive(int[] arr, int left, int right) {
        if (left >= right) return;

        int mid = (left + right) / 2;

        mergeSortRecursive(arr, left, mid);
        mergeSortRecursive(arr, mid + 1, right);

        merge(arr, left, mid, right);
    }

    private void mergeSortWithSteps(int[] arr, int[] temp, int left, int right, List<SortStep> steps) {
        if (left >= right) return;
        int mid = (left + right) / 2;
        mergeSortWithSteps(arr, temp, left, mid, steps);
        mergeSortWithSteps(arr, temp, mid + 1, right, steps);
        mergeWithSteps(arr, temp, left, mid, right, steps);
    }

    private void merge(int[] arr, int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;

        int[] L = new int[n1];
        int[] R = new int[n2];

        for (int i = 0; i < n1; i++) L[i] = arr[left + i];
        for (int i = 0; i < n2; i++) R[i] = arr[mid + 1 + i];

        int i = 0, j = 0, k = left;

        while (i < n1 && j < n2) {
            if (L[i] <= R[j]) {
                arr[k++] = L[i++];
            } else {
                arr[k++] = R[j++];
            }
        }

        while (i < n1) arr[k++] = L[i++];
        while (j < n2) arr[k++] = R[j++];
    }

    private void mergeWithSteps(int[] arr, int[] temp, int left, int mid, int right, List<SortStep> steps) {
        int i = left;
        int j = mid + 1;
        int k = left;
        while (i <= mid && j <= right) {
            int ca = i;
            int cb = j;
            if (arr[i] <= arr[j]) {
                temp[k] = arr[i++];
            } else {
                temp[k] = arr[j++];
            }
            recordStep(arr, temp, left, right, ca, cb, k, steps);
            k++;
        }
        while (i <= mid) {
            int ca = i;
            temp[k] = arr[i++];
            recordStep(arr, temp, left, right, ca, -1, k, steps);
            k++;
        }
        while (j <= right) {
            int cb = j;
            temp[k] = arr[j++];
            recordStep(arr, temp, left, right, -1, cb, k, steps);
            k++;
        }
        for (int t = left; t <= right; t++) {
            arr[t] = temp[t];
        }
    }

    private static void recordStep(
            int[] arr,
            int[] temp,
            int left,
            int right,
            int compareA,
            int compareB,
            int writeIndex,
            List<SortStep> steps
    ) {
        int[] vis = arr.clone();
        for (int p = left; p <= writeIndex; p++) {
            vis[p] = temp[p];
        }
        steps.add(new SortStep(vis, compareA, compareB, left, right));
    }
}
