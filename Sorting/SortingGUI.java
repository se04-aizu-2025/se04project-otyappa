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

        InputMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
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
    private final JTextField manualInputField = new JTextField(28);
    private final JLabel filePathLabel = new JLabel("No file selected");
    private File selectedFile;
    private final JLabel statusLabel = new JLabel("Ready");

    // 再生速度 (100 = 標準)
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
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout());

        JPanel control = new JPanel();
        control.setLayout(new BoxLayout(control, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

        row1.add(new JLabel("Algorithm"));
        sorters.forEach(s -> sorterCombo.addItem(s.name()));
        row1.add(sorterCombo);

        row1.add(new JLabel("Input"));
        inputModeCombo.setSelectedItem(InputMode.RANDOM);
        inputModeCombo.addActionListener(this::onInputModeChanged);
        row1.add(inputModeCombo);

        row1.add(new JLabel("Size"));
        row1.add(sizeSpinner);

        row1.add(new JLabel("Seed"));
        row1.add(seedSpinner);

        row1.add(new JLabel("Speed"));
        speedSlider.setPreferredSize(new Dimension(160, 40));
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(ev -> speedValueLabel.setText(speedSlider.getValue() + "%"));
        row1.add(speedSlider);
        row1.add(speedValueLabel);

        row2.add(new JLabel("Values"));
        manualInputField.setText("5 1 4 2 3");
        row2.add(manualInputField);

        fileButton = new JButton("Choose File");
        fileButton.addActionListener(this::onChooseFile);
        row2.add(fileButton);
        row2.add(filePathLabel);

        generateButton = new JButton("Generate");
        generateButton.addActionListener(this::onGenerate);
        row2.add(generateButton);

        stepButton = new JButton("Step");
        stepButton.addActionListener(this::onStep);
        row2.add(stepButton);

        resetButton = new JButton("Reset");
        resetButton.addActionListener(this::onReset);
        row2.add(resetButton);

        control.add(row1);
        control.add(row2);

        frame.add(control, BorderLayout.NORTH);
        frame.add(chartPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setVisible(true);
        updateInputModeUI();
        onGenerate(null); // 初期表示でランダム生成を反映
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
        if (fileButton != null) {
            fileButton.setEnabled(file);
        }
    }

    private void onChooseFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            filePathLabel.setText(selectedFile.getName());
            statusLabel.setText("Selected file: " + selectedFile.getAbsolutePath());
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
            } else if (mode == InputMode.MANUAL) {
                currentData = parseInts(manualInputField.getText());
                chartPanel.setData(currentData);
                statusLabel.setText("Loaded: manual input size=" + currentData.length);
            } else if (mode == InputMode.FILE) {
                if (selectedFile == null) {
                    onChooseFile(null);
                }
                if (selectedFile == null) {
                    statusLabel.setText("No file selected");
                    return;
                }
                String content = Files.readString(selectedFile.toPath());
                currentData = parseInts(content);
                chartPanel.setData(currentData);
                statusLabel.setText("Loaded: file size=" + currentData.length);
            }
        } catch (Exception ex) {
            statusLabel.setText("Generate failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // リセット step 状態
        stopStepTimer();
    }

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
        StepSortable sorter = sorters.get(sorterIdx);
        startStepPlayback(sorter);
    }

    private void onReset(ActionEvent e) {
        stopStepTimer();
        InputMode mode = (InputMode) inputModeCombo.getSelectedItem();
        if (mode == null) mode = InputMode.RANDOM;
        if (mode == InputMode.RANDOM) {
            onGenerate(null);
        } else {
            currentData = new int[0];
            chartPanel.setData(currentData);
            statusLabel.setText("Reset");
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

        if (stepSteps.isEmpty()) {
            statusLabel.setText("No steps for " + stepAlgoName);
            return;
        }

        setControlsEnabled(false);

        int n = currentData.length;
        playbackTick = 0;
        double nSafe = Math.max(1, n);
        int targetMs = (int) Math.round(PLAYBACK_TARGET_MS_AT_N50 * Math.pow(50.0 / nSafe, PLAYBACK_SIZE_SCALING_EXP));
        targetMs = Math.max(PLAYBACK_TARGET_MIN_MS, Math.min(PLAYBACK_TARGET_MAX_MS, targetMs));
        playbackTotalTicks = Math.max(1, targetMs / PLAYBACK_FRAME_DELAY_MS);
        playbackStepsPerTickBase = Math.max(1.0, stepSteps.size()) / (double) playbackTotalTicks;
        playbackAccumulator = 0.0;

        stepTimer = new Timer(PLAYBACK_FRAME_DELAY_MS, ev -> {
            if (stepIndex >= stepSteps.size()) {
                statusLabel.setText("Step mode: completed");
                stopStepTimer();
                setControlsEnabled(true);
                return;
            }

            double p = Math.min(1.0, playbackTick / (double) playbackTotalTicks);
            double rate = PLAYBACK_MIN_RATE + (PLAYBACK_MAX_RATE - PLAYBACK_MIN_RATE) * p;
            // スライダーで倍率をかける（100%が標準）
            rate *= (speedSlider.getValue() / 100.0);

            playbackAccumulator += playbackStepsPerTickBase * rate;

            int work = 0;
            int maxWork = 5000;
            while (playbackAccumulator >= 1.0 && work < maxWork && stepIndex < stepSteps.size()) {
                SortStep s = stepSteps.get(stepIndex++);
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

            if (stepCompareA >= 0 || stepCompareB >= 0) {
                statusLabel.setText(stepAlgoName + ": compare " + stepCompareA + " & " + stepCompareB
                        + " (" + stepIndex + "/" + stepSteps.size() + ")");
            } else {
                statusLabel.setText(stepAlgoName + " (" + stepIndex + "/" + stepSteps.size() + ")");
            }
            playbackTick = Math.min(playbackTick + 1, 1_000_000_000);
        });
        stepTimer.setInitialDelay(0);
        stepTimer.setCoalesce(true);
        stepTimer.start();
    }

    private void stopStepTimer() {
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
    }

    private void setControlsEnabled(boolean enabled) {
        inputModeCombo.setEnabled(enabled);
        sorterCombo.setEnabled(enabled);
        speedSlider.setEnabled(enabled);
        if (enabled) {
            updateInputModeUI();
        } else {
            sizeSpinner.setEnabled(false);
            seedSpinner.setEnabled(false);
            manualInputField.setEnabled(false);
            filePathLabel.setEnabled(false);
            if (fileButton != null) {
                fileButton.setEnabled(false);
            }
        }
    }

    private static int[] parseInts(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Input is empty");
        }
        Pattern p = Pattern.compile("-?\\d+");
        Matcher m = p.matcher(text);
        List<Integer> values = new ArrayList<>();
        while (m.find()) {
            values.add(Integer.parseInt(m.group()));
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("No integers found");
        }
        int[] arr = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = values.get(i);
        }
        return arr;
    }

    private static class ChartPanel extends JPanel {
        private int[] data = new int[0];
        private int compareA = -1;
        private int compareB = -1;
        private int mergeRangeFrom = -1;
        private int mergeRangeTo = -1;

        void setData(int[] data) {
            this.data = data;
            repaint();
        }

        void setCompare(int a, int b) {
            compareA = a;
            compareB = b;
        }

        void setMergeRange(int from, int to) {
            mergeRangeFrom = from;
            mergeRangeTo = to;
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
                if (mergeRangeFrom >= 0 && mergeRangeTo >= 0 && i >= mergeRangeFrom && i <= mergeRangeTo) {
                    g.setColor(new Color(200, 220, 255));
                    g.fillRect(x, 0, barWidth - 1, height);
                }

                boolean isCompare = i == compareA || i == compareB;

                if (isCompare) {
                    g.setColor(new Color(220, 80, 80));
                } else {
                    g.setColor(new Color(80, 140, 220));
                }
                g.fillRect(x, y, barWidth - 1, barHeight);
                if (isCompare) {
                    g.setColor(Color.BLACK);
                    g.drawRect(x, y, barWidth - 2, barHeight);
                }
            }
        }
    }
}
