package sorting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class SortTestEngine {

    private record TestCase(DataGenerator.Pattern pattern, int size, long seed) {}

    public static void main(String[] args) {
        List<TestCase> cases = new ArrayList<>();
        cases.add(new TestCase(DataGenerator.Pattern.ASCENDING, 50, 0));

        DataGenerator generator = new DataGenerator();
        Sorter sorter = new MergeSort();

        boolean allPassed = true;
        for (TestCase tc : cases) {
            int[] data = generator.generate(tc.pattern, tc.size, tc.seed);
            int[] original = Arrays.copyOf(data, data.length);

            long start = System.nanoTime();
            sorter.sort(data);
            long elapsedMicros = (System.nanoTime() - start) / 1_000;

            boolean ok = isSorted(data);
            if (!ok) {
                allPassed = false;
                System.out.printf("[FAIL] %-15s pattern=%-14s size=%4d seed=%3d elapsed=%6d µs%n",
                        sorter.name(), tc.pattern(), tc.size(), tc.seed(), elapsedMicros);
                System.out.println("  input : " + Arrays.toString(original));
                System.out.println("  output: " + Arrays.toString(data));
            } else {
                System.out.printf("[PASS] %-15s pattern=%-14s size=%4d seed=%3d elapsed=%6d µs%n",
                        sorter.name(), tc.pattern(), tc.size(), tc.seed(), elapsedMicros);
            }
        }

        if (allPassed) {
            System.out.println("All test cases passed.");
        } else {
            System.out.println("Some test cases failed. See logs above.");
        }
    }

    private static boolean isSorted(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            if (arr[i - 1] > arr[i]) {
                return false;
            }
        }
        return true;
    }
}

