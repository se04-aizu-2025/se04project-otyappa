import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SortingGUI extends JFrame {

    // ===== GUI部品 =====
    private JComboBox<StepSortable> algorithmBox;
    private JButton generateButton;
    private JButton startButton;
    private JButton resetButton;
    private JSlider speedSlider;
    private JLabel infoLabel;
    private DrawPanel drawPanel;

    // ===== データ =====
    private int[] originalData;
    private List<SortStep> steps;
    private int stepIndex;
    private Timer timer;

    public SortingGUI() {
        setTitle("Sorting Visualizer");
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        initActions();
    }

    private void initComponents() {
        algorithmBox = new JComboBox<>();
        algorithmBox.addItem(new BubbleSort());
        algorithmBox.addItem(new SelectionSort());
        algorithmBox.addItem(new MergeSort());

        generateButton = new JButton("Generate");
        startButton = new JButton("Start");
        resetButton = new JButton("Reset");

        // ★ 速度調整スライダー（左：遅い → 右：速い）
        speedSlider = new JSlider(1, 100, 50);
        speedSlider.setToolTipText("Speed");

        infoLabel = new JLabel("Generate data to start");

        drawPanel = new DrawPanel();
    }

    private void layoutComponents() {
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Algorithm:"));
        topPanel.add(algorithmBox);
        topPanel.add(generateButton);
        topPanel.add(startButton);
        topPanel.add(resetButton);
        topPanel.add(new JLabel("Speed"));
        topPanel.add(speedSlider);

        add(topPanel, BorderLayout.NORTH);
        add(drawPanel, BorderLayout.CENTER);
        add(infoLabel, BorderLayout.SOUTH);
    }

    private void initActions() {
        generateButton.addActionListener(e -> generateData());
        startButton.addActionListener(e -> startSorting());
        resetButton.addActionListener(e -> reset());

        // Timer はここでは仮の delay
        timer = new Timer(50, e -> playStep());
    }

    private void generateData() {
        // DataGenerator は static メソッドがある前提
        originalData = DataGenerator.generateRandomData(50, 10, 300);
        steps = null;
        stepIndex = 0;

        drawPanel.setData(originalData, -1, -1, -1, -1);
        infoLabel.setText("Data generated");
    }

    private void startSorting() {
        if (originalData == null) return;

        if (steps == null) {
            StepSortable sorter = (StepSortable) algorithmBox.getSelectedItem();
            steps = sorter.steps(originalData);
            stepIndex = 0;
            infoLabel.setText("Running: " + sorter.name());
        }

        // ★ スライダーで速度変更（値が大きいほど速い）
        int delay = Math.max(1, 101 - speedSlider.getValue());
        timer.setDelay(delay);
        timer.start();
    }

    private void playStep() {
        if (steps == null || stepIndex >= steps.size()) {
            timer.stop();
            infoLabel.setText("Finished");
            return;
        }

        SortStep step = steps.get(stepIndex);
        drawPanel.setData(
                step.data,
                step.compareA,
                step.compareB,
                step.rangeL,
                step.rangeR
        );
        stepIndex++;
    }

    private void reset() {
        timer.stop();
        stepIndex = 0;

        if (originalData != null) {
            drawPanel.setData(originalData, -1, -1, -1, -1);
            infoLabel.setText("Reset");
        }
    }

    // ===== 描画パネル =====
    private static class DrawPanel extends JPanel {
        private int[] data;
        private int compareA, compareB, rangeL, rangeR;

        public void setData(int[] data, int a, int b, int l, int r) {
            this.data = data.clone();
            this.compareA = a;
            this.compareB = b;
            this.rangeL = l;
            this.rangeR = r;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data == null) return;

            int w = getWidth();
            int h = getHeight();
            int n = data.length;

            int max = 1;
            for (int v : data) max = Math.max(max, v);

            int barWidth = Math.max(1, w / n);

            for (int i = 0; i < n; i++) {
                int barHeight = (int) ((double) data[i] / max * (h - 20));
                int x = i * barWidth;
                int y = h - barHeight;

                if (i == compareA || i == compareB) {
                    g.setColor(Color.RED);          // 比較中
                } else if (rangeL <= i && i <= rangeR && rangeL != -1) {
                    g.setColor(new Color(100, 100, 255)); // 処理範囲
                } else {
                    g.setColor(Color.BLUE);         // 通常
                }

                g.fillRect(x, y, barWidth - 2, barHeight);
            }
        }
    }

    // ===== main =====
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SortingGUI().setVisible(true));
    }
}
