import java.util.Random;

public class DataGenerator {

    // いろんな入力パターンを作れるように残しておく（今後GUI拡張しやすい）
    public enum Pattern {
        RANDOM,
        ASCENDING,
        DESCENDING,
        ALMOST_SORTED,
        MANY_DUPLICATES
    }

    // ----------------------------
    // 互換用（GUIが呼んでるメソッド）
    // ----------------------------

    /**
     * GUIが呼んでいる想定のメソッド。
     * 例: DataGenerator.generateRandomData(50, 10, 300)
     *
     * @param size 配列サイズ
     * @param min  最小値（含む）
     * @param max  最大値（含む）
     */
    public static int[] generateRandomData(int size, int min, int max) {
        return generateRandomData(size, min, max, System.nanoTime());
    }

    /**
     * seed指定版（再現性が必要なとき用）
     */
    public static int[] generateRandomData(int size, int min, int max, long seed) {
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
        if (min > max) throw new IllegalArgumentException("min must be <= max");

        int[] arr = new int[size];
        Random rand = new Random(seed);

        int range = (max - min) + 1; // maxも含むため +1
        for (int i = 0; i < size; i++) {
            arr[i] = min + rand.nextInt(range);
        }
        return arr;
    }

    // ----------------------------
    // GUI(君のSortingGUI)が使ってる形
    // ----------------------------

    /**
     * GUI側が  generator.generate(Pattern.RANDOM, size, seed) みたいに呼ぶ想定のメソッド
     */
    public int[] generate(Pattern pattern, int size, long seed) {
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");

        int[] arr = new int[size];
        Random rand = new Random(seed);

        switch (pattern) {
            case RANDOM -> fillRandom(arr, rand);
            case ASCENDING -> fillAscending(arr);
            case DESCENDING -> fillDescending(arr);
            case ALMOST_SORTED -> fillAlmostSorted(arr, rand);
            case MANY_DUPLICATES -> fillManyDuplicates(arr, rand);
            default -> fillRandom(arr, rand);
        }
        return arr;
    }

    private void fillRandom(int[] arr, Random rand) {
        for (int i = 0; i < arr.length; i++) arr[i] = rand.nextInt(100) + 1;
    }

    private void fillAscending(int[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = i + 1;
    }

    private void fillDescending(int[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = arr.length - i;
    }

    private void fillAlmostSorted(int[] arr, Random rand) {
        fillAscending(arr);
        // 少しだけシャッフルして「ほぼソート済み」にする
        int swaps = Math.max(1, arr.length / 10);
        for (int k = 0; k < swaps; k++) {
            int i = rand.nextInt(arr.length);
            int j = rand.nextInt(arr.length);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private void fillManyDuplicates(int[] arr, Random rand) {
        // 値の種類を少なくして重複多めに
        for (int i = 0; i < arr.length; i++) arr[i] = rand.nextInt(10) + 1;
    }
}
