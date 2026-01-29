import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

public class SortingGUI {

    private enum InputMode {
        RANDOM("Random"),
        MANUAL("Manual Input"),
        FILE("Load From File");

        private final String label;

        InputMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private final DataGenerator generator = new DataGenerator();
    private final List<StepSortable> sorters = List.of(
            new MergeSort(),
            new BubbleSort(),
            new SelectionSort()
    );

    private int[] currentData = new int[0];

    private final ChartPanel chartPanel = new ChartPanel();

    private final JComboBox<InputMode> inputModeCombo = new JComboBox<>(InputMode.values());
    private final JComboBox<String> sorterCombo = new JComboBox<>();

    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 500, 5));
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(42, 0, Integer.MAX_VALUE, 1));

    private final JTextField manualInputField = new JTextField(18);
    private final JLabel filePathLabel = new JLabel("No file selected");
    private File selectedFile;

    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel metricsLabel = new JLabel("Speed 100%");

    // ---- Result panel (完了後も残す) ----
    private final JTextArea resultArea = new JTextArea(12, 24);
    private final JScrollPane resultScroll = new JScrollPane(resultArea);

    // ---- Metrics (A) ----
    private int metricCompares = 0;
    private int metricWrites = 0;
    private int[] metricPrevData = null;
    private long metricStartNs = 0L;
    private long metricEndNs = 0L;

    // Speed slider
    private final JSlider speedSlider = new JSlider(10, 300, 100);
    private final JLabel speedValueLabel = new JLabel("100%");

    private JButton generateButton;
    private JButton fileButton;
    private JButton stepButton;
    private JButton resetButton;

    private Timer stepTimer;

    private List<SortStep> stepSteps = List.of();
    private int stepIndex = 0;

    private int stepCompareA = -1;
    private int stepCompareB = -1;
    private int stepRangeL = -1;
    private int stepRangeR = -1;

    private String stepAlgoName = "";

    private int playbackTick = 0;
    private int playbackTotalTicks = 0;
    private double playbackStepsPerTickBase = 0.0;
    private double playbackAccumulator = 0.0;

    private static final int PLAYBACK_TARGET_MS_AT_N50 = 10_000;
    private static final int PLAYBACK_FRAME_DELAY_MS = 20;
    private static final double PLAYBACK_MIN_RATE = 0.6;
    private static final double PLAYBACK_MAX_RATE = 1.4;
    private static final int PLAYBACK_TARGET_MIN_MS = 2_000;
    private static final int PLAYBACK_TARGET_MAX_MS = 15_000;
    private static final double PLAYBACK_SIZE_SCALING_EXP = 0.50;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SortingGUI().start());
    }

    private void start() {
        JFrame frame = new JFrame("Sorting GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1050, 650);
        frame.setLayout(new BorderLayout());

        // ---- Control panel (GridBagLayoutで崩れない) ----
        JPanel control = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.gridy = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;

        // Algorithm
        control.add(new JLabel("Algorithm"), c);
        c.gridx++;
        sorters.forEach(s -> sorterCombo.addItem(s.name()));
        control.add(sorterCombo, c);

        // Input
        c.gridx++;
        control.add(new JLabel("Input"), c);
        c.gridx++;
        inputModeCombo.setSelectedItem(InputMode.RANDOM);
        inputModeCombo.addActionListener(this::onInputModeChanged);
        control.add(inputModeCombo, c);

        // Size
        c.gridx++;
        control.add(new JLabel("Size"), c);
        c.gridx++;
        control.add(sizeSpinner, c);

        // Seed
        c.gridx++;
        control.add(new JLabel("Seed"), c);
        c.gridx++;
        control.add(seedSpinner, c);

        // Speed label
        c.gridx++;
        control.add(new JLabel("Speed"), c);

        // Speed slider (横に伸びる)
        c.gridx++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        speedSlider.setPaintTicks(true);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPreferredSize(new Dimension(220, 40));
        control.add(speedSlider, c);

        // Speed value label
        c.gridx++;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0.0;
        control.add(speedValueLabel, c);

        // 2行目へ
        c.gridy = 1;
        c.gridx = 0;

        // Values
        control.add(new JLabel("Values"), c);
        c.gridx++;
        c.gridwidth = 2;
        control.add(manualInputField, c);
        c.gridwidth = 1;

        // Choose file
        c.gridx += 2;
        fileButton = new JButton("Choose File");
        fileButton.addActionListener(this::onChooseFile);
        control.add(fileButton, c);

        c.gridx++;
        filePathLabel.setPreferredSize(new Dimension(140, 18));
        control.add(filePathLabel, c);

        // Generate / Step / Reset
        c.gridx++;
        generateButton = new JButton("Generate");
        generateButton.addActionListener(this::onGenerate);
        control.add(generateButton, c);

        c.gridx++;
        stepButton = new JButton("Step");
        stepButton.addActionListener(this::onStep);
        control.add(stepButton, c);

        c.gridx++;
        resetButton = new JButton("Reset");
        resetButton.addActionListener(this::onReset);
        control.add(resetButton, c);

        // Speed listener（％も右下も絶対追従）
        speedSlider.addChangeListener(ev -> {
            speedValueLabel.setText(speedSlider.getValue() + "%");
            if (stepTimer != null) {
                stepTimer.setDelay(calcDelayMs());
            }
            updateMetricsLabel();
        });
        speedValueLabel.setText(speedSlider.getValue() + "%");

        // Result panel
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultScroll.setBorder(BorderFactory.createTitledBorder("Result (saved)"));

        frame.add(control, BorderLayout.NORTH);
        frame.add(chartPanel, BorderLayout.CENTER);
        frame.add(resultScroll, BorderLayout.EAST);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(metricsLabel, BorderLayout.EAST);
        frame.add(statusPanel, BorderLayout.SOUTH);

        frame.setVisible(true);

        updateInputModeUI();
        onGenerate(null);
        appendResult("Ready. (Speed " + speedSlider.getValue() + "%)");
    }

    private void onInputModeChanged(ActionEvent e) {
        updateInputModeUI();
    }

    private void updateInputModeUI() {
        InputMode mode = (InputMode) inputModeCombo.getSelectedItem();
        if (mode == null) mode = InputMode.RANDOM;

        boolean random = mode == InputMode.RANDOM;
        boolean manual = mode == InputMode.MANUAL;
        boolean file = mode == InputMode.FILE;

        sizeSpinner.setEnabled(random);
        seedSpinner.setEnabled(random);
        manualInputField.setEnabled(manual);

        filePathLabel.setEnabled(file);
        if (fileButton != null) fileButton.setEnabled(file);
    }

    private void onChooseFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            filePathLabel.setText(selectedFile.getName());
            statusLabel.setText("Selected file: " + selectedFile.getAbsolutePath());
            appendResult("Selected file: " + selectedFile.getAbsolutePath());
        }
    }

    private void onGenerate(ActionEvent e) {
        try {
            InputMode mode = (InputMode) inputModeCombo.getSelectedItem();
            if (mode == null) mode = InputMode.RANDOM;

            if (mode == InputMode.RANDOM) {
                int size = (int) sizeSpinner.getValue();
                long seed = ((Number) seedSpinner.getValue()).longValue();
                currentData = generator.generate(DataGenerator.Pattern.RANDOM, size, seed);
                chartPanel.setData(currentData);
                statusLabel.setText("Generated: RANDOM size=" + size + " seed=" + seed);
                appendResult("Generated RANDOM size=" + size + " seed=" + seed);
            } else if (mode == InputMode.MANUAL) {
                currentData = parseInts(manualInputField.getText());
                chartPanel.setData(currentData);
                statusLabel.setText("Loaded: manual input size=" + currentData.length);
                appendResult("Loaded MANUAL size=" + currentData.length);
            } else if (mode == InputMode.FILE) {
                if (selectedFile == null) onChooseFile(null);
                if (selectedFile == null) {
                    statusLabel.setText("No file selected");
                    return;
                }
                String content = Files.readString(selectedFile.toPath());
                currentData = parseInts(content);
                chartPanel.setData(currentData);
                statusLabel.setText("Loaded: file size=" + currentData.length);
                appendResult("Loaded FILE size=" + currentData.length);
            }
        } catch (Exception ex) {
            statusLabel.setText("Generate failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            appendResult("ERROR: " + ex.getMessage());
            return;
        }

        stopStepTimer(false); // 生成時に結果ログは残す
    }

    private void onStep(ActionEvent e) {
        stopStepTimer(false);

        if (currentData == null || currentData.length == 0) {
            onGenerate(null);
        }

        int sorterIdx = sorterCombo.getSelectedIndex();
        if (sorterIdx < 0 || sorterIdx >= sorters.size()) {
            statusLabel.setText("No sorter selected");
            return;
        }

        StepSortable sorter = sorters.get(sorterIdx);
        startStepPlayback(sorter);
    }

    private void onReset(ActionEvent e) {
        stopStepTimer(false);

        InputMode mode = (InputMode) inputModeCombo.getSelectedItem();
        if (mode == null) mode = InputMode.RANDOM;

        if (mode == InputMode.RANDOM) {
            onGenerate(null);
        } else {
            currentData = new int[0];
            chartPanel.setData(currentData);
            statusLabel.setText("Reset");
            appendResult("Reset.");
        }
        setControlsEnabled(true);
    }

    private void startStepPlayback(StepSortable sorter) {
        stepAlgoName = sorter.name();
        stepSteps = sorter.steps(currentData);

        stepIndex = 0;
        stepCompareA = -1;
        stepCompareB = -1;
        stepRangeL = -1;
        stepRangeR = -1;

        metricCompares = 0;
        metricWrites = 0;
        metricPrevData = null;
        metricStartNs = System.nanoTime();
        metricEndNs = 0L;

        if (stepSteps.isEmpty()) {
            statusLabel.setText("No steps for " + stepAlgoName);
            appendResult("No steps for " + stepAlgoName);
            return;
        }

        appendResult("START " + stepAlgoName + " | n=" + currentData.length + " | totalSteps=" + stepSteps.size()
                + " | speed=" + speedSlider.getValue() + "%");

        setControlsEnabled(false);

        int n = currentData.length;
        playbackTick = 0;
        double nSafe = Math.max(1, n);
        int targetMs = (int) Math.round(PLAYBACK_TARGET_MS_AT_N50 * Math.pow(50.0 / nSafe, PLAYBACK_SIZE_SCALING_EXP));
        targetMs = Math.max(PLAYBACK_TARGET_MIN_MS, Math.min(PLAYBACK_TARGET_MAX_MS, targetMs));
        playbackTotalTicks = Math.max(1, targetMs / PLAYBACK_FRAME_DELAY_MS);
        playbackStepsPerTickBase = Math.max(1.0, stepSteps.size()) / (double) playbackTotalTicks;
        playbackAccumulator = 0.0;

        stepTimer = new Timer(calcDelayMs(), ev -> {
            if (stepIndex >= stepSteps.size()) {
                metricEndNs = System.nanoTime();
                statusLabel.setText("Step mode: completed");
                updateMetricsLabel();
                appendResult(makeSummaryLine("DONE"));
                stopStepTimer(true);     // 完了時は最終結果を保持
                setControlsEnabled(true);
                return;
            }

            double p = Math.min(1.0, playbackTick / (double) playbackTotalTicks);
            double rate = PLAYBACK_MIN_RATE + (PLAYBACK_MAX_RATE - PLAYBACK_MIN_RATE) * p;
            playbackAccumulator += playbackStepsPerTickBase * rate;

            int work = 0;
            int maxWork = 5000;
            while (playbackAccumulator >= 1.0 && work < maxWork && stepIndex < stepSteps.size()) {
                SortStep s = stepSteps.get(stepIndex++);

                if (s.compareA >= 0 || s.compareB >= 0) metricCompares++;

                if (metricPrevData != null) {
                    int diff = 0;
                    int len = Math.min(metricPrevData.length, s.data.length);
                    for (int i = 0; i < len; i++) {
                        if (metricPrevData[i] != s.data[i]) diff++;
                    }
                    metricWrites += diff;
                }
                metricPrevData = s.data;

                currentData = s.data;
                stepCompareA = s.compareA;
                stepCompareB = s.compareB;
                stepRangeL = s.rangeL;
                stepRangeR = s.rangeR;

                playbackAccumulator -= 1.0;
                work++;
            }

            chartPanel.setCompare(stepCompareA, stepCompareB);
            chartPanel.setMergeRange(stepRangeL, stepRangeR);
            chartPanel.setData(currentData);

            // MergeSortでもわかりやすく：rangeがあるなら出す
            if (stepRangeL >= 0 && stepRangeR >= 0) {
                statusLabel.setText(stepAlgoName + " (" + stepIndex + "/" + stepSteps.size() + ")"
                        + " | range [" + stepRangeL + "," + stepRangeR + "]");
            } else if (stepCompareA >= 0 || stepCompareB >= 0) {
                statusLabel.setText(stepAlgoName + ": compare " + stepCompareA + " & " + stepCompareB
                        + " (" + stepIndex + "/" + stepSteps.size() + ")");
            } else {
                statusLabel.setText(stepAlgoName + " (" + stepIndex + "/" + stepSteps.size() + ")");
            }

            updateMetricsLabel();
            playbackTick = Math.min(playbackTick + 1, 1_000_000_000);
        });

        stepTimer.setInitialDelay(0);
        stepTimer.setCoalesce(true);
        stepTimer.start();
        updateMetricsLabel();
    }

    // 完了時はメトリクス残したいので、clearMetrics=false の stop を分ける
    private void stopStepTimer(boolean keepLastMetrics) {
        if (stepTimer != null) {
            stepTimer.stop();
            stepTimer = null;
        }
        stepSteps = List.of();
        stepIndex = 0;
        stepAlgoName = "";

        chartPanel.setCompare(-1, -1);
        chartPanel.setMergeRange(-1, -1);

        playbackTick = 0;
        playbackTotalTicks = 0;
        playbackStepsPerTickBase = 0.0;
        playbackAccumulator = 0.0;

        if (!keepLastMetrics) {
            metricCompares = 0;
            metricWrites = 0;
            metricPrevData = null;
            metricStartNs = 0L;
            metricEndNs = 0L;
            updateMetricsLabel();
        }
    }

    private void setControlsEnabled(boolean enabled) {
        inputModeCombo.setEnabled(enabled);
        sorterCombo.setEnabled(enabled);

        // 再生中も speed は触れる（ここ重要）
        speedSlider.setEnabled(true);

        if (enabled) {
            updateInputModeUI();
        } else {
            sizeSpinner.setEnabled(false);
            seedSpinner.setEnabled(false);
            manualInputField.setEnabled(false);
            filePathLabel.setEnabled(false);
            if (fileButton != null) fileButton.setEnabled(false);
        }
    }

    // Speed% -> Timer delay(ms)
    private int calcDelayMs() {
        int base = PLAYBACK_FRAME_DELAY_MS; // 20ms
        int v = Math.max(10, speedSlider.getValue());
        int d = (int) Math.round(base * (100.0 / v));
        return Math.max(1, Math.min(200, d));
    }

    private void updateMetricsLabel() {
        int sp = speedSlider.getValue();
        String base = "Speed " + sp + "% (delay " + calcDelayMs() + "ms)";

        if (metricStartNs == 0L) {
            metricsLabel.setText(base);
            return;
        }

        long end = (metricEndNs != 0L) ? metricEndNs : System.nanoTime();
        double sec = (end - metricStartNs) / 1_000_000_000.0;

        metricsLabel.setText(base
                + " | Steps " + stepIndex + (stepSteps.isEmpty() ? "" : "/" + stepSteps.size())
                + " | Compares " + metricCompares
                + " | Writes " + metricWrites
                + String.format(" | Time %.2fs", sec));
    }

    private String makeSummaryLine(String tag) {
        int sp = speedSlider.getValue();
        long end = (metricEndNs != 0L) ? metricEndNs : System.nanoTime();
        double sec = (metricStartNs == 0L) ? 0.0 : (end - metricStartNs) / 1_000_000_000.0;

        int total = (metricStartNs == 0L) ? 0 : (metricEndNs != 0L ? stepSteps.size() : stepIndex);

        return tag + " " + (stepAlgoName.isEmpty() ? "(unknown)" : stepAlgoName)
                + " | n=" + (currentData == null ? 0 : currentData.length)
                + " | speed=" + sp + "% (delay " + calcDelayMs() + "ms)"
                + " | steps=" + (metricEndNs != 0L ? "DONE" : stepIndex)
                + " | compares=" + metricCompares
                + " | writes=" + metricWrites
                + String.format(" | time=%.2fs", sec);
    }

    private void appendResult(String line) {
        resultArea.append(line + "\n");
        resultArea.setCaretPosition(resultArea.getDocument().getLength());
    }

    private static int[] parseInts(String text) {
        if (text == null) throw new IllegalArgumentException("Input is empty");
        Pattern p = Pattern.compile("-?\\d+");
        Matcher m = p.matcher(text);
        List<Integer> values = new ArrayList<>();
        while (m.find()) values.add(Integer.parseInt(m.group()));
        if (values.isEmpty()) throw new IllegalArgumentException("No integers found");

        int[] a = new int[values.size()];
        for (int i = 0; i < values.size(); i++) a[i] = values.get(i);
        return a;
    }

    private static class ChartPanel extends JPanel {
        private int[] data = new int[0];
        private int compareA = -1;
        private int compareB = -1;
        private int mergeRangeFrom = -1;
        private int mergeRangeTo = -1;

        public void setData(int[] data) {
            this.data = (data == null) ? new int[0] : data;
            repaint();
        }

        public void setCompare(int a, int b) {
            compareA = a;
            compareB = b;
            repaint();
        }

        public void setMergeRange(int from, int to) {
            mergeRangeFrom = from;
            mergeRangeTo = to;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data == null || data.length == 0) return;

            int w = getWidth();
            int h = getHeight();
            int n = data.length;

            int max = 1;
            for (int v : data) max = Math.max(max, v);

            int barW = Math.max(1, w / n);

            for (int i = 0; i < n; i++) {
                int v = data[i];
                int barH = (int) ((v / (double) max) * (h * 0.90));
                int x = i * barW;
                int y = h - barH;

                if (i == compareA || i == compareB) {
                    g.setColor(Color.RED);
                } else if (mergeRangeFrom >= 0 && mergeRangeTo >= 0 && i >= mergeRangeFrom && i <= mergeRangeTo) {
                    g.setColor(new Color(80, 140, 255));
                } else {
                    g.setColor(Color.GRAY);
                }

                g.fillRect(x, y, barW - 1, barH);
            }
        }
    }
}
