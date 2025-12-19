public class SelectionSort {

    // 配列を選択ソートで並び替えるメソッド
    public static void selectionSort(int[] array) {

        int n = array.length;

        // 配列の先頭から順に並び替える
        for (int i = 0; i < n - 1; i++) {

            // まだ並び替えていない部分の最小値の位置
            int minIndex = i;

            // 最小値を探す
            for (int j = i + 1; j < n; j++) {
                if (array[j] < array[minIndex]) {
                    minIndex = j;
                }
            }

            // 最小値を先頭と交換
            int temp = array[minIndex];
            array[minIndex] = array[i];
            array[i] = temp;
        }
    }

    // 動作確認用
    public static void main(String[] args) {
        int[] data = {5, 3, 4, 1, 2};

        selectionSort(data);

        // 結果を表示
        for (int num : data) {
            System.out.print(num + " ");
        }
    }
}
