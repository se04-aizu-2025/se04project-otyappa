package sorting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class SortingGUI {

    private final DataGenerator generator = new DataGenerator();
    private final Sorter sorter = new MergeSort();
    private int[] currentData = new int[0];
    private final ChartPanel chartPanel = new ChartPanel();
    private final JComboBox<DataGenerator.Pattern> patternCombo =
            new JComboBox<>(new DataGenerator.Pattern[]{DataGenerator.Pattern.RANDOM});
    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 500, 5));
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(42, 0, Integer.MAX_VALUE, 1));
    private final JLabel statusLabel = new JLabel("Ready");

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

        control.add(new JLabel("Algorithm: Merge Sort"));

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

        JButton sortButton = new JButton("Sort");
        sortButton.addActionListener(this::onSort);
        control.add(sortButton);

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
    }

    private void onSort(ActionEvent e) {
        if (currentData == null || currentData.length == 0) {
            onGenerate(null); // データ未生成なら生成してからソート
        }

        int[] dataCopy = currentData.clone();

        statusLabel.setText("Sorting with " + sorter.name() + "...");
        setControlsEnabled(false);

        SwingWorker<Void, int[]> worker = new SwingWorker<>() {
            long elapsedMicros;

            @Override
            protected Void doInBackground() {
                long start = System.nanoTime();
                sorter.sort(dataCopy);
                elapsedMicros = (System.nanoTime() - start) / 1_000;
                return null;
            }

            @Override
            protected void done() {
                currentData = dataCopy;
                chartPanel.setData(currentData);
                statusLabel.setText("Done: " + sorter.name() + " elapsed=" + elapsedMicros + " µs");
                setControlsEnabled(true);
            }
        };
        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        patternCombo.setEnabled(enabled);
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

