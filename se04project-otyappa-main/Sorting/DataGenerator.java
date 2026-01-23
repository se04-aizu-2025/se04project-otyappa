import java.util.Random;
public class DataGenerator {

    public enum Pattern {
        RANDOM,
        ASCENDING,
        DESCENDING,
        ALMOST_SORTED,
        MANY_DUPLICATES
    }

    public int[] generate(Pattern pattern, int size, long seed) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        int[] arr = new int[size];
        Random rand = new Random(seed);

        switch (pattern) {
            case RANDOM -> fillRandom(arr, rand, size);
            case ASCENDING -> fillAscending(arr);
            case DESCENDING -> fillDescending(arr);
            case ALMOST_SORTED -> fillAlmostSorted(arr, rand);
            case MANY_DUPLICATES -> fillManyDuplicates(arr, rand);
            default -> throw new IllegalArgumentException("Unknown pattern: " + pattern);
        }
        return arr;
    }

    private void fillRandom(int[] arr, Random rand, int size) {
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(size * 3 + 1);
        }
    }

    private void fillAscending(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i;
        }
    }

    private void fillDescending(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = arr.length - i;
        }
    }

    private void fillAlmostSorted(int[] arr, Random rand) {
        fillAscending(arr);
        if (arr.length <= 1) {
            return;
        }
        int swaps = Math.max(1, arr.length / 20);
        for (int k = 0; k < swaps; k++) {
            int i = rand.nextInt(arr.length);
            int j = rand.nextInt(arr.length);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private void fillManyDuplicates(int[] arr, Random rand) {
        // 小さい値域で乱数を振ることで重複を増やす
        int range = Math.max(1, Math.min(10, arr.length / 3));
        for (int i = 0; i < arr.length; i++) {
            arr[i] = rand.nextInt(range);
        }
    }
}
