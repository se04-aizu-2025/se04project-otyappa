package sorting;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;

public class SortingGUI {

    private final DataGenerator generator = new DataGenerator();
    private final List<Sorter> sorters = List.of(
            new MergeSort(),
            new BubbleSort(),
            new SelectionSort()
    );
    private int[] currentData = new int[0];
    private final ChartPanel chartPanel = new ChartPanel();
    private final JComboBox<DataGenerator.Pattern> patternCombo =
            new JComboBox<>(new DataGenerator.Pattern[]{DataGenerator.Pattern.RANDOM});
    private final JComboBox<String> sorterCombo = new JComboBox<>();
    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 500, 5));
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(42, 0, Integer.MAX_VALUE, 1));
    private final JLabel statusLabel = new JLabel("Ready");
    private JButton stepButton;
    private JButton resetButton;
    private Timer stepTimer;

    // step-by-step (Bubble Sortのみ対応)
    private boolean stepModeActive = false;
    private int stepI = 0;
    private int stepJ = 0;
    private List<int[]> mergeSteps = new ArrayList<>();
    private int mergeStepIndex = 0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SortingGUI().start());
    }

    private void start() {
        JFrame frame = new JFrame("Sorting GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout());

        JPanel control = new JPanel();
        control.setLayout(new FlowLayout(FlowLayout.LEFT));

        control.add(new JLabel("Algorithm"));
        sorters.forEach(s -> sorterCombo.addItem(s.name()));
        control.add(sorterCombo);

        control.add(new JLabel("Pattern: RANDOM"));
        patternCombo.setSelectedItem(DataGenerator.Pattern.RANDOM);
        patternCombo.setEnabled(false);

        control.add(new JLabel("Size"));
        control.add(sizeSpinner);

        control.add(new JLabel("Seed"));
        control.add(seedSpinner);

        JButton generateButton = new JButton("Generate");
        generateButton.addActionListener(this::onGenerate);
        control.add(generateButton);

        stepButton = new JButton("Step");
        stepButton.addActionListener(this::onStep);
        control.add(stepButton);

        resetButton = new JButton("Reset");
        resetButton.addActionListener(this::onReset);
        control.add(resetButton);

        frame.add(control, BorderLayout.NORTH);
        frame.add(chartPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setVisible(true);
        onGenerate(null); // 初期表示でランダム生成を反映
    }

    private void onGenerate(ActionEvent e) {
        int size = (int) sizeSpinner.getValue();
        long seed = ((Number) seedSpinner.getValue()).longValue();
        DataGenerator.Pattern pattern = DataGenerator.Pattern.RANDOM;
        currentData = generator.generate(pattern, size, seed);
        chartPanel.setData(currentData);
        statusLabel.setText("Generated: " + pattern + " size=" + size + " seed=" + seed);

        // リセット step 状態
        stopStepTimer();
        stepModeActive = false;
        stepI = 0;
        stepJ = 0;
        mergeSteps.clear();
        mergeStepIndex = 0;
    }

    // 一気にソートするボタンは削除したので onSort は不要

    private void onStep(ActionEvent e) {
        stopStepTimer();

        if (currentData == null || currentData.length == 0) {
            onGenerate(null);
        }

        int sorterIdx = sorterCombo.getSelectedIndex();
        if (sorterIdx < 0 || sorterIdx >= sorters.size()) {
            statusLabel.setText("No sorter selected");
            return;
        }
        Sorter sorter = sorters.get(sorterIdx);

        if (sorter instanceof BubbleSort) {
            if (!stepModeActive) {
                // 初回ステップ: 状態リセット
                stepModeActive = true;
                stepI = 0;
                stepJ = 0;
                statusLabel.setText("Step mode: Bubble Sort");
            }

            setControlsEnabled(false);

            stepTimer = new Timer(50, ev -> {
                boolean finished = advanceBubbleStep();
                chartPanel.setData(currentData);
                if (finished) {
                    statusLabel.setText("Step mode: completed");
                    stopStepTimer();
                    setControlsEnabled(true);
                } else {
                    statusLabel.setText("Step mode: i=" + stepI + " j=" + stepJ);
                }
            });
            stepTimer.start();
        } else if (sorter instanceof MergeSort) {
            startMergeStepMode();
        } else if (sorter instanceof SelectionSort) {
            startSelectionStepMode();
        } else {
            statusLabel.setText("Step mode: supported for Bubble / Merge / Selection only");
        }
    }

    private void onReset(ActionEvent e) {
        stopStepTimer();
        stepModeActive = false;
        stepI = 0;
        stepJ = 0;
        mergeSteps.clear();
        mergeStepIndex = 0;
        onGenerate(null);
        setControlsEnabled(true);
        statusLabel.setText("Reset and generated new data");
    }

    private boolean advanceBubbleStep() {
        int n = currentData.length;
        if (n <= 1) return true;
        if (stepI >= n - 1) return true;

        if (currentData[stepJ] > currentData[stepJ + 1]) {
            int tmp = currentData[stepJ];
            currentData[stepJ] = currentData[stepJ + 1];
            currentData[stepJ + 1] = tmp;
        }

        stepJ++;
        if (stepJ >= n - 1 - stepI) {
            stepJ = 0;
            stepI++;
        }

        return stepI >= n - 1;
    }

    private void startMergeStepMode() {
        mergeSteps.clear();
        mergeStepIndex = 0;
        statusLabel.setText("Step mode: Merge Sort");

        int[] work = currentData.clone();
        recordMergeSteps(work);
        setControlsEnabled(false);

        stepTimer = new Timer(50, ev -> {
            if (mergeStepIndex >= mergeSteps.size()) {
                statusLabel.setText("Step mode: completed");
                stopStepTimer();
                setControlsEnabled(true);
                return;
            }
            currentData = mergeSteps.get(mergeStepIndex).clone();
            chartPanel.setData(currentData);
            mergeStepIndex++;
            statusLabel.setText("Merge step " + mergeStepIndex + "/" + mergeSteps.size());
        });
        stepTimer.start();
    }

    private void startSelectionStepMode() {
        mergeSteps.clear();
        mergeStepIndex = 0;
        statusLabel.setText("Step mode: Selection Sort");

        int[] work = currentData.clone();
        recordSelectionSteps(work);
        setControlsEnabled(false);

        stepTimer = new Timer(50, ev -> {
            if (mergeStepIndex >= mergeSteps.size()) {
                statusLabel.setText("Step mode: completed");
                stopStepTimer();
                setControlsEnabled(true);
                return;
            }
            currentData = mergeSteps.get(mergeStepIndex).clone();
            chartPanel.setData(currentData);
            mergeStepIndex++;
            statusLabel.setText("Selection step " + mergeStepIndex + "/" + mergeSteps.size());
        });
        stepTimer.start();
    }

    private void recordMergeSteps(int[] arr) {
        int[] temp = new int[arr.length];
        mergeSortWithSnapshot(arr, temp, 0, arr.length - 1);
    }

    private void recordSelectionSteps(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
            }
            int tmp = arr[minIndex];
            arr[minIndex] = arr[i];
            arr[i] = tmp;
            mergeSteps.add(arr.clone());
        }
    }

    private void mergeSortWithSnapshot(int[] arr, int[] temp, int left, int right) {
        if (left >= right) return;
        int mid = (left + right) / 2;
        mergeSortWithSnapshot(arr, temp, left, mid);
        mergeSortWithSnapshot(arr, temp, mid + 1, right);
        mergeWithSnapshot(arr, temp, left, mid, right);
    }

    private void mergeWithSnapshot(int[] arr, int[] temp, int left, int mid, int right) {
        int i = left;
        int j = mid + 1;
        int k = left;
        while (i <= mid && j <= right) {
            if (arr[i] <= arr[j]) {
                temp[k++] = arr[i++];
            } else {
                temp[k++] = arr[j++];
            }
        }
        while (i <= mid) temp[k++] = arr[i++];
        while (j <= right) temp[k++] = arr[j++];
        for (int t = left; t <= right; t++) {
            arr[t] = temp[t];
            mergeSteps.add(arr.clone());
        }
    }

    private void stopStepTimer() {
        if (stepTimer != null) {
            stepTimer.stop();
            stepTimer = null;
        }
    }

    private void setControlsEnabled(boolean enabled) {
        patternCombo.setEnabled(enabled);
        sorterCombo.setEnabled(enabled);
        sizeSpinner.setEnabled(enabled);
        seedSpinner.setEnabled(enabled);
    }

    private static class ChartPanel extends JPanel {
        private int[] data = new int[0];

        void setData(int[] data) {
            this.data = data;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data == null || data.length == 0) return;

            int width = getWidth();
            int height = getHeight();
            int barWidth = Math.max(1, width / data.length);
            int max = 1;
            for (int v : data) {
                if (v > max) max = v;
            }
            for (int i = 0; i < data.length; i++) {
                int v = data[i];
                int barHeight = (int) ((v / (double) max) * (height - 20));
                int x = i * barWidth;
                int y = height - barHeight;
                g.setColor(new Color(80, 140, 220));
                g.fillRect(x, y, barWidth - 1, barHeight);
            }
        }
    }
}

