package sorting;

public class SelectionSort implements Sorter {

    @Override
    public String name() {
        return "Selection Sort";
    }

    @Override
    public void sort(int[] arr) {
        int n = arr.length;

        // 配列の先頭から順に並び替える
        for (int i = 0; i < n - 1; i++) {
            // まだ並び替えていない部分の最小値の位置
            int minIndex = i;

            // 最小値を探す
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
            }

            // 最小値を先頭と交換
            int temp = arr[minIndex];
            arr[minIndex] = arr[i];
            arr[i] = temp;
        }
    }
}
