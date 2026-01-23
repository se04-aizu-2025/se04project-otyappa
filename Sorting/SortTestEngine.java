import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class SortTestEngine {

    private record TestCase(DataGenerator.Pattern pattern, int size, long seed) {}

    public static void main(String[] args) {
        List<TestCase> cases = new ArrayList<>();
        for (DataGenerator.Pattern pattern : DataGenerator.Pattern.values()) {
            cases.add(new TestCase(pattern, 0, 0));
            cases.add(new TestCase(pattern, 1, 0));
            cases.add(new TestCase(pattern, 10, 1));
            cases.add(new TestCase(pattern, 50, 42));
            cases.add(new TestCase(pattern, 200, 99));
        }

        List<Sorter> sorters = List.of(
                new MergeSort(),
                new BubbleSort(),
                new SelectionSort()
        );

        DataGenerator generator = new DataGenerator();

        boolean allPassed = true;
        for (Sorter sorter : sorters) {
            for (TestCase tc : cases) {
                int[] data = generator.generate(tc.pattern, tc.size, tc.seed);
                int[] original = Arrays.copyOf(data, data.length);
                int[] expected = Arrays.copyOf(data, data.length);
                Arrays.sort(expected);

                long start = System.nanoTime();
                sorter.sort(data);
                long elapsedMicros = (System.nanoTime() - start) / 1_000;

                boolean ok = isSorted(data) && Arrays.equals(data, expected);
                if (!ok) {
                    allPassed = false;
                    System.out.printf("[FAIL] %-15s pattern=%-14s size=%4d seed=%3d elapsed=%6d µs%n",
                            sorter.name(), tc.pattern(), tc.size(), tc.seed(), elapsedMicros);
                    System.out.println("  input   : " + Arrays.toString(original));
                    System.out.println("  expected: " + Arrays.toString(expected));
                    System.out.println("  output  : " + Arrays.toString(data));
                } else {
                    System.out.printf("[PASS] %-15s pattern=%-14s size=%4d seed=%3d elapsed=%6d µs%n",
                            sorter.name(), tc.pattern(), tc.size(), tc.seed(), elapsedMicros);
                }
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
