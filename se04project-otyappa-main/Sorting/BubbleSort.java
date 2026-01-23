import java.util.ArrayList;
import java.util.List;

public class BubbleSort implements StepSortable {

    @Override
    public String name() {
        return "Bubble Sort";
    }

    // テスト用（一気にソート）
    @Override
    public void sort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }

    // GUI用（途中経過つき）
    @Override
    public List<SortStep> steps(int[] input) {
        int[] arr = input.clone();          // 元データ破壊防止
        List<SortStep> steps = new ArrayList<>();
        int n = arr.length;

        // 初期状態
        steps.add(new SortStep(arr.clone(), -1, -1, -1, -1));

        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {

                // 比較中の2点を表示
                steps.add(new SortStep(arr.clone(), j, j + 1, -1, -1));

                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;

                    // 交換後の状態を表示
                    steps.add(new SortStep(arr.clone(), j, j + 1, -1, -1));
                }
            }
        }

        // 完成状態
        steps.add(new SortStep(arr.clone(), -1, -1, -1, -1));
        return steps;
    }
}
