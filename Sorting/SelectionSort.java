package sorting;

import java.util.ArrayList;
import java.util.List;

/**
 * Selection Sort
 * - sort(int[]) : テスト用（SortTestEngineが呼ぶ）
 * - steps(int[]) : GUIの「Step」で使う（途中経過を返す）
 */
public class SelectionSort implements StepSortable, Sorter {

    @Override
    public String name() {
        return "Selection Sort";
    }

    /**
     * 普通に並び替えるだけ（テスト用）
     */
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

            // swap
            int tmp = arr[minIndex];
            arr[minIndex] = arr[i];
            arr[i] = tmp;
        }
    }

    /**
     * 途中経過（ステップ）を返す（GUI用）
     * - data: その時点の配列スナップショット
     * - compareA/compareB: 比較中のインデックス（強調表示用）
     * - rangeL/rangeR: 「いま注目している範囲」（ここでは i..n-1 を渡す）
     */
    @Override
    public List<SortStep> steps(int[] input) {
        int[] arr = input.clone();                // 入力を壊さないためにコピー
        List<SortStep> steps = new ArrayList<>();

        int n = arr.length;
        if (n <= 1) return steps;

        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;

            // 「この周回での開始」を記録（compareは i と minIndex）
            record(steps, arr, i, minIndex, i, n - 1);

            for (int j = i + 1; j < n; j++) {
                // いま比較しているのは j と minIndex
                record(steps, arr, j, minIndex, i, n - 1);

                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                    // 最小候補が更新された瞬間も記録（見た目わかりやすい）
                    record(steps, arr, j, minIndex, i, n - 1);
                }
            }

            // swap前も記録（i と minIndex を強調）
            record(steps, arr, i, minIndex, i, n - 1);

            // swap
            int tmp = arr[minIndex];
            arr[minIndex] = arr[i];
            arr[i] = tmp;

            // swap後も記録（どこが入れ替わったか見える）
            record(steps, arr, i, minIndex, i, n - 1);
        }

        // 最後に「完成状態」をもう1回入れておく（止まったとき気持ちいい）
        record(steps, arr, -1, -1, 0, n - 1);

        return steps;
    }

    /**
     * steps に 1 ステップ追加する共通処理
     */
    private static void record(List<SortStep> steps, int[] arr,
                               int compareA, int compareB,
                               int rangeL, int rangeR) {
        steps.add(new SortStep(arr.clone(), compareA, compareB, rangeL, rangeR));
    }
}
