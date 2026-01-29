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
        @Override public String toString() { return label; }
    }

    private final DataGenerator generator = new DataGenerator();
    private final List<StepSortable> sorters = List.of(
            new MergeSort(),
            new BubbleSort(),
            new SelectionSort()
    );

    // ----- Data -----
    private int[] baseData = new int[0];
    private File selectedFile;

    // ----- UI Controls -----
    private final JComboBox<InputMode> inputModeCombo = new JComboBox<>(InputMode.values());
    private final JComboBox<String> algoLeftCombo = new JComboBox<>();
    private final JComboBox<String> algoRightCombo = new JComboBox<>();

    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 500, 5));
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(42, 0, Integer.MAX_VALUE, 1));
    private final JTextField manualInputField = new JTextField(22);

    private final JLabel filePathLabel = new JLabel("No file selected");

    private final JSlider speedSlider = new JSlider(10, 300, 100);
    private final JLabel speedValueLabel = new JLabel("100%");

    private final JCheckBox compareCheck = new JCheckBox("Compare mode (split view)");

    private JButton chooseFileButton;
    private JButton generateButton;
    private JButton stepButton;
    private JButton resetButton;

    // ----- Center View -----
    private final CardLayout centerCards = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerCards);

    private final ChartPanel chartSingle = new ChartPanel();
    private final ChartPanel chartLeft = new ChartPanel();
    private final ChartPanel chartRight = new ChartPanel();

    private final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            wrapTitled(chartLeft, "Left"),
            wrapTitled(chartRight, "Right")
    );

    // ----- Status + Result Panels -----
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel metricsLabel = new JLabel("Speed 100%");

    private final JTextArea latestArea = new JTextArea(12, 26);
    private final JTextArea historyArea = new JTextArea(14, 26);

    // ----- Playback / Steps -----
    private Timer timer;

    // Left run
    private List<SortStep> stepsL = List.of();
    private int idxL = 0;
    private int[] curL = new int[0];
    private int compareAL = -1, compareBL = -1, rangeLL = -1, rangeRL = -1;

    // Right run
    private List<SortStep> stepsR = List.of();
    private int idxR = 0;
    private int[] curR = new int[0];
    private int compareAR = -1, compareBR = -1, rangeLR = -1, rangeRR = -1;

    // Metrics (approx)
    private long startNs = 0L;
    private long endNs = 0L;

    private int comparesL = 0, writesL = 0;
    private int comparesR = 0, writesR = 0;
    private int[] prevL = null, prevR = null;

    // Smoothness control
    private double accumulator = 0.0;
    private double stepsPerTickBase = 1.0;
    private int tick = 0;
    private int totalTicks = 1;

    // Playback shaping (kept simple + safe)
    private static final int TARGET_MS_AT_N50 = 10_000;
    private static final int FRAME_DELAY_MS = 20;
    private static final int TARGET_MIN_MS = 2_000;
    private static final int TARGET_MAX_MS = 15_000;
    private static final double SIZE_EXP = 0.50;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SortingGUI().start());
    }

    private void start() {
        JFrame frame = new JFrame("Sorting GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1150, 700);
        frame.setLayout(new BorderLayout());

        // Fill algo combos
        sorters.forEach(s -> {
            algoLeftCombo.addItem(s.name());
            algoRightCombo.addItem(s.name());
        });
        algoLeftCombo.setSelectedIndex(0);
        algoRightCombo.setSelectedIndex(1 < sorters.size() ? 1 : 0);

        // ----- Controls (uniform grid) -----
        JPanel controls = buildControlsPanel();
        frame.add(controls, BorderLayout.NORTH);

        // ----- Center cards -----
        centerPanel.add(chartSingle, "SINGLE");
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);
        centerPanel.add(splitPane, "SPLIT");
        frame.add(centerPanel, BorderLayout.CENTER);

        // ----- Right result panel -----
        JPanel rightPanel = buildRightPanel();
        frame.add(rightPanel, BorderLayout.EAST);

        // ----- Bottom status -----
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        bottom.add(statusLabel, BorderLayout.WEST);
        bottom.add(metricsLabel, BorderLayout.EAST);
        frame.add(bottom, BorderLayout.SOUTH);

        // Listeners
        inputModeCombo.addActionListener(e -> updateInputModeUI());
        compareCheck.addActionListener(e -> {
            updateCompareUI();
            // update titles on split pane
            updateChartTitles();
        });

        speedSlider.addChangeListener(e -> {
            speedValueLabel.setText(speedSlider.getValue() + "%");
            if (timer != null) timer.setDelay(calcDelayMs());
            updateMetricsLabel();
        });
        speedValueLabel.setText(speedSlider.getValue() + "%");

        frame.setVisible(true);

        // Initial UI state
        updateInputModeUI();
        updateCompareUI();
        updateChartTitles();

        // Start with generated data
        onGenerate(null);
        appendHistory("Ready. Speed=" + speedSlider.getValue() + "%");
    }

    private JPanel buildControlsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        // Helper: label style
        Font labelFont = p.getFont().deriveFont(Font.PLAIN, 12f);

        // Row 0
        int row = 0;
        addLabel(p, c, row, 0, "Algorithm (L)", labelFont);
        c.gridx = 1; c.gridy = row; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.0;
        algoLeftCombo.setPreferredSize(new Dimension(160, 26));
        p.add(algoLeftCombo, c);

        addLabel(p, c, row, 2, "Algorithm (R)", labelFont);
        c.gridx = 3; c.gridy = row;
        algoRightCombo.setPreferredSize(new Dimension(160, 26));
        p.add(algoRightCombo, c);

        addLabel(p, c, row, 4, "Input", labelFont);
        c.gridx = 5; c.gridy = row;
        inputModeCombo.setPreferredSize(new Dimension(140, 26));
        p.add(inputModeCombo, c);

        // Row 1
        row = 1;
        addLabel(p, c, row, 0, "Size", labelFont);
        c.gridx = 1; c.gridy = row;
        sizeSpinner.setPreferredSize(new Dimension(90, 26));
        p.add(sizeSpinner, c);

        addLabel(p, c, row, 2, "Seed", labelFont);
        c.gridx = 3; c.gridy = row;
        seedSpinner.setPreferredSize(new Dimension(120, 26));
        p.add(seedSpinner, c);

        addLabel(p, c, row, 4, "Speed", labelFont);
        c.gridx = 5; c.gridy = row; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        speedSlider.setPreferredSize(new Dimension(260, 40));
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPaintTicks(true);
        p.add(speedSlider, c);

        c.gridx = 6; c.gridy = row; c.fill = GridBagConstraints.NONE; c.weightx = 0.0;
        p.add(speedValueLabel, c);

        // Row 2
        row = 2;
        addLabel(p, c, row, 0, "Values", labelFont);
        c.gridx = 1; c.gridy = row; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        manualInputField.setPreferredSize(new Dimension(300, 26));
        manualInputField.setText("5 1 4 2 3");
        p.add(manualInputField, c);
        c.gridwidth = 1;

        chooseFileButton = new JButton("Choose File");
        chooseFileButton.addActionListener(this::onChooseFile);

        c.gridx = 4; c.gridy = row; c.fill = GridBagConstraints.NONE; c.weightx = 0.0;
        p.add(chooseFileButton, c);

        c.gridx = 5; c.gridy = row; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        filePathLabel.setPreferredSize(new Dimension(200, 18));
        p.add(filePathLabel, c);

        // Row 3 - Buttons + Compare
        row = 3;

        compareCheck.setSelected(false);
        c.gridx = 0; c.gridy = row; c.gridwidth = 4; c.fill = GridBagConstraints.NONE; c.weightx = 0.0;
        p.add(compareCheck, c);
        c.gridwidth = 1;

        generateButton = new JButton("Generate");
        generateButton.addActionListener(this::onGenerate);

        stepButton = new JButton("Start");
        stepButton.addActionListener(this::onStart);

        resetButton = new JButton("Reset");
        resetButton.addActionListener(this::onReset);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.add(generateButton);
        btns.add(stepButton);
        btns.add(resetButton);

        c.gridx = 4; c.gridy = row; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        p.add(btns, c);
        c.gridwidth = 1;

        return p;
    }

    private static void addLabel(JPanel p, GridBagConstraints c, int row, int col, String text, Font f) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(f);
        c.gridx = col;
        c.gridy = row;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0.0;
        p.add(lbl, c);
    }

    private JPanel buildRightPanel() {
        latestArea.setEditable(false);
        latestArea.setLineWrap(true);
        latestArea.setWrapStyleWord(true);
        latestArea.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        latestArea.setBackground(new Color(245, 245, 245));

        historyArea.setEditable(false);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane latestScroll = new JScrollPane(latestArea);
        JScrollPane historyScroll = new JScrollPane(historyArea);

        latestScroll.setBorder(BorderFactory.createTitledBorder("Latest Result"));
        historyScroll.setBorder(BorderFactory.createTitledBorder("History"));

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 8));

        latestScroll.setPreferredSize(new Dimension(300, 250));
        historyScroll.setPreferredSize(new Dimension(300, 350));

        right.add(latestScroll);
        right.add(Box.createVerticalStrut(8));
        right.add(historyScroll);

        return right;
    }

    private static JPanel wrapTitled(JComponent comp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private void updateChartTitles() {
        // titles are handled by the titled borders around charts (Left/Right).
        // You can enhance later if needed.
    }

    private void updateCompareUI() {
        boolean compare = compareCheck.isSelected();
        algoRightCombo.setEnabled(compare);
        centerCards.show(centerPanel, compare ? "SPLIT" : "SINGLE");
        // Keep split divider reasonable
        splitPane.setDividerLocation(0.5);
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

        chooseFileButton.setEnabled(file);
        filePathLabel.setEnabled(file);
    }

    // ---------------- Actions ----------------

    private void onChooseFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            filePathLabel.setText(selectedFile.getName());
            statusLabel.setText("Selected file: " + selectedFile.getAbsolutePath());
            appendHistory("Selected file: " + selectedFile.getAbsolutePath());
        }
    }

    private void onGenerate(ActionEvent e) {
        stopPlayback(false);

        try {
            InputMode mode = (InputMode) inputModeCombo.getSelectedItem();
            if (mode == null) mode = InputMode.RANDOM;

            if (mode == InputMode.RANDOM) {
                int size = (int) sizeSpinner.getValue();
                long seed = ((Number) seedSpinner.getValue()).longValue();
                baseData = generator.generate(DataGenerator.Pattern.RANDOM, size, seed);
                statusLabel.setText("Generated RANDOM size=" + size + " seed=" + seed);
                appendHistory(blockLine("Generated", "RANDOM | size=" + size + " | seed=" + seed));
            } else if (mode == InputMode.MANUAL) {
                baseData = parseInts(manualInputField.getText());
                statusLabel.setText("Loaded MANUAL size=" + baseData.length);
                appendHistory(blockLine("Loaded", "MANUAL | size=" + baseData.length));
            } else {
                if (selectedFile == null) onChooseFile(null);
                if (selectedFile == null) {
                    statusLabel.setText("No file selected");
                    return;
                }
                String content = Files.readString(selectedFile.toPath());
                baseData = parseInts(content);
                statusLabel.setText("Loaded FILE size=" + baseData.length);
                appendHistory(blockLine("Loaded", "FILE | size=" + baseData.length));
            }

            // Reflect on chart(s)
            if (compareCheck.isSelected()) {
                chartLeft.setData(baseData);
                chartRight.setData(baseData);
            } else {
                chartSingle.setData(baseData);
            }

            latestArea.setText("===== Latest Result =====\n\n(press Start)");
            updateMetricsLabel();

        } catch (Exception ex) {
            statusLabel.setText("Generate failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            appendHistory(blockLine("ERROR", ex.getMessage()));
        }
    }

    private void onStart(ActionEvent e) {
        stopPlayback(false);

        if (baseData == null || baseData.length == 0) {
            onGenerate(null);
            if (baseData == null || baseData.length == 0) return;
        }

        boolean compare = compareCheck.isSelected();

        StepSortable leftSorter = sorters.get(algoLeftCombo.getSelectedIndex());
        StepSortable rightSorter = sorters.get(algoRightCombo.getSelectedIndex());

        // Prepare runs
        curL = baseData.clone();
        stepsL = leftSorter.steps(curL);
        idxL = 0;
        compareAL = compareBL = rangeLL = rangeRL = -1;

        if (compare) {
            curR = baseData.clone();
            stepsR = rightSorter.steps(curR);
            idxR = 0;
            compareAR = compareBR = rangeLR = rangeRR = -1;
        } else {
            curR = new int[0];
            stepsR = List.of();
            idxR = 0;
        }

        // Reset metrics
        startNs = System.nanoTime();
        endNs = 0L;

        comparesL = writesL = 0;
        comparesR = writesR = 0;
        prevL = null;
        prevR = null;

        // Playback pacing based on dataset size and total steps (use max for compare)
        int totalSteps = Math.max(stepsL.size(), compare ? stepsR.size() : 0);
        initPacing(baseData.length, totalSteps);

        setControlsEnabled(false);

        String runTitle = compare
                ? ("START | Left=" + leftSorter.name() + " vs Right=" + rightSorter.name()
                   + " | n=" + baseData.length + " | speed=" + speedSlider.getValue() + "%")
                : ("START | Algo=" + leftSorter.name() + " | n=" + baseData.length + " | speed=" + speedSlider.getValue() + "%");

        statusLabel.setText(runTitle);
        appendHistory(sep());
        appendHistory(runTitle);
        appendHistory("Left steps=" + stepsL.size() + (compare ? (" | Right steps=" + stepsR.size()) : ""));

        timer = new Timer(calcDelayMs(), ev -> onTick(compare, leftSorter, rightSorter));
        timer.setInitialDelay(0);
        timer.setCoalesce(true);
        timer.start();
        updateMetricsLabel();
    }

    private void onReset(ActionEvent e) {
        stopPlayback(false);
        setControlsEnabled(true);

        // Keep current baseData view
        if (compareCheck.isSelected()) {
            chartLeft.setData(baseData);
            chartRight.setData(baseData);
        } else {
            chartSingle.setData(baseData);
        }

        statusLabel.setText("Reset");
        latestArea.setText("===== Latest Result =====\n\n(press Start)");
        updateMetricsLabel();
        appendHistory(blockLine("Reset", ""));
    }

    // ---------------- Playback core ----------------

    private void onTick(boolean compareMode, StepSortable leftSorter, StepSortable rightSorter) {
        // Completion check
        boolean doneL = idxL >= stepsL.size();
        boolean doneR = !compareMode || idxR >= stepsR.size();

        if (doneL && doneR) {
            endNs = System.nanoTime();
            if (timer != null) timer.stop();
            timer = null;

            // Update final view (last data already set)
            statusLabel.setText("Completed");
            setControlsEnabled(true);

            // Latest result (pretty)
            latestArea.setText(buildLatestResult(compareMode, leftSorter.name(), rightSorter.name()));

            // History block
            appendHistory(buildHistoryBlock(compareMode, leftSorter.name(), rightSorter.name()));

            updateMetricsLabel();
            return;
        }

        // Work units per tick (smooth)
        accumulator += stepsPerTickBase;
        int work = 0;
        int maxWork = 6000;

        while (accumulator >= 1.0 && work < maxWork) {
            // Advance left if not done
            if (idxL < stepsL.size()) {
                SortStep s = stepsL.get(idxL++);
                updateMetricsForStep(true, s);
                curL = s.data;
                compareAL = s.compareA;
                compareBL = s.compareB;
                rangeLL = s.rangeL;
                rangeRL = s.rangeR;
            }

            // Advance right if compare and not done
            if (compareMode && idxR < stepsR.size()) {
                SortStep s = stepsR.get(idxR++);
                updateMetricsForStep(false, s);
                curR = s.data;
                compareAR = s.compareA;
                compareBR = s.compareB;
                rangeLR = s.rangeL;
                rangeRR = s.rangeR;
            }

            accumulator -= 1.0;
            work++;

            // if both are done, break early
            if (idxL >= stepsL.size() && (!compareMode || idxR >= stepsR.size())) break;
        }

        // Draw
        if (compareMode) {
            chartLeft.setCompare(compareAL, compareBL);
            chartLeft.setMergeRange(rangeLL, rangeRL);
            chartLeft.setData(curL);

            chartRight.setCompare(compareAR, compareBR);
            chartRight.setMergeRange(rangeLR, rangeRR);
            chartRight.setData(curR);

            statusLabel.setText("Running | Left " + leftSorter.name() + " (" + idxL + "/" + stepsL.size() + ")"
                    + "  vs  Right " + rightSorter.name() + " (" + idxR + "/" + stepsR.size() + ")");
        } else {
            chartSingle.setCompare(compareAL, compareBL);
            chartSingle.setMergeRange(rangeLL, rangeRL);
            chartSingle.setData(curL);

            // Merge range hint
            if (rangeLL >= 0 && rangeRL >= 0) {
                statusLabel.setText("Running | " + leftSorter.name() + " (" + idxL + "/" + stepsL.size()
                        + ") | range [" + rangeLL + "," + rangeRL + "]");
            } else {
                statusLabel.setText("Running | " + leftSorter.name() + " (" + idxL + "/" + stepsL.size() + ")");
            }
        }

        tick++;
        updateMetricsLabel();
    }

    private void initPacing(int n, int totalSteps) {
        tick = 0;
        accumulator = 0.0;

        double nSafe = Math.max(1, n);
        int targetMs = (int) Math.round(TARGET_MS_AT_N50 * Math.pow(50.0 / nSafe, SIZE_EXP));
        targetMs = Math.max(TARGET_MIN_MS, Math.min(TARGET_MAX_MS, targetMs));

        totalTicks = Math.max(1, targetMs / FRAME_DELAY_MS);
        stepsPerTickBase = Math.max(1.0, totalSteps) / (double) totalTicks;

        // speed affects timer delay, not steps/tick (so it's visually obvious)
    }

    private void updateMetricsForStep(boolean isLeft, SortStep s) {
        // compares: count if compare indices set
        if (s.compareA >= 0 || s.compareB >= 0) {
            if (isLeft) comparesL++; else comparesR++;
        }

        // writes: approximate by diff count vs previous array
        if (isLeft) {
            if (prevL != null) writesL += countDiff(prevL, s.data);
            prevL = s.data;
        } else {
            if (prevR != null) writesR += countDiff(prevR, s.data);
            prevR = s.data;
        }
    }

    private static int countDiff(int[] a, int[] b) {
        int len = Math.min(a.length, b.length);
        int diff = 0;
        for (int i = 0; i < len; i++) if (a[i] != b[i]) diff++;
        return diff;
    }

    private void stopPlayback(boolean keepLatest) {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        stepsL = List.of(); stepsR = List.of();
        idxL = idxR = 0;

        if (!keepLatest) {
            startNs = endNs = 0L;
            comparesL = writesL = comparesR = writesR = 0;
            prevL = prevR = null;
        }
        updateMetricsLabel();
    }

    private void setControlsEnabled(boolean enabled) {
        // allow speed slider always
        speedSlider.setEnabled(true);

        inputModeCombo.setEnabled(enabled);
        algoLeftCombo.setEnabled(enabled);
        compareCheck.setEnabled(enabled);
        algoRightCombo.setEnabled(enabled && compareCheck.isSelected());

        generateButton.setEnabled(enabled);
        stepButton.setEnabled(enabled);
        resetButton.setEnabled(true); // reset always allowed

        if (enabled) {
            updateInputModeUI();
        } else {
            sizeSpinner.setEnabled(false);
            seedSpinner.setEnabled(false);
            manualInputField.setEnabled(false);
            chooseFileButton.setEnabled(false);
            filePathLabel.setEnabled(false);
        }
    }

    private int calcDelayMs() {
        int base = FRAME_DELAY_MS; // 20ms
        int v = Math.max(10, speedSlider.getValue());
        int d = (int) Math.round(base * (100.0 / v));
        return Math.max(1, Math.min(200, d));
    }

    private void updateMetricsLabel() {
        int sp = speedSlider.getValue();
        String base = "Speed " + sp + "% (delay " + calcDelayMs() + "ms)";

        if (startNs == 0L) {
            metricsLabel.setText(base);
            return;
        }

        long end = (endNs != 0L) ? endNs : System.nanoTime();
        double sec = (end - startNs) / 1_000_000_000.0;

        boolean compare = compareCheck.isSelected();
        if (!compare) {
            metricsLabel.setText(base
                    + " | Steps " + idxL + (stepsL.isEmpty() ? "" : "/" + stepsL.size())
                    + " | Compares " + comparesL
                    + " | Writes " + writesL
                    + String.format(" | Time %.2fs", sec));
        } else {
            metricsLabel.setText(base
                    + " | L " + idxL + "/" + stepsL.size() + " C" + comparesL + " W" + writesL
                    + " | R " + idxR + "/" + stepsR.size() + " C" + comparesR + " W" + writesR
                    + String.format(" | Time %.2fs", sec));
        }
    }

    // ---------------- Result formatting ----------------

    private String buildLatestResult(boolean compareMode, String leftName, String rightName) {
        long end = (endNs != 0L) ? endNs : System.nanoTime();
        double sec = (startNs == 0L) ? 0.0 : (end - startNs) / 1_000_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("===== Latest Result =====\n\n");
        sb.append("Input  : ").append(inputModeCombo.getSelectedItem()).append("\n");
        sb.append("Size   : ").append(baseData == null ? 0 : baseData.length).append("\n");
        sb.append("Speed  : ").append(speedSlider.getValue()).append("% (").append(calcDelayMs()).append("ms)\n");
        sb.append(String.format("Time   : %.2fs\n", sec));
        sb.append("\n");

        if (!compareMode) {
            sb.append("Algorithm : ").append(leftName).append("\n");
            sb.append("Steps     : ").append(stepsL.size()).append("\n");
            sb.append("Compares  : ").append(comparesL).append("\n");
            sb.append("Writes    : ").append(writesL).append("\n");
        } else {
            sb.append("[Left]\n");
            sb.append("Algorithm : ").append(leftName).append("\n");
            sb.append("Steps     : ").append(stepsL.size()).append("\n");
            sb.append("Compares  : ").append(comparesL).append("\n");
            sb.append("Writes    : ").append(writesL).append("\n\n");

            sb.append("[Right]\n");
            sb.append("Algorithm : ").append(rightName).append("\n");
            sb.append("Steps     : ").append(stepsR.size()).append("\n");
            sb.append("Compares  : ").append(comparesR).append("\n");
            sb.append("Writes    : ").append(writesR).append("\n");
        }

        sb.append("\n(Note) Writes is an approximate value based on step-to-step differences.\n");
        return sb.toString();
    }

    private String buildHistoryBlock(boolean compareMode, String leftName, String rightName) {
        long end = (endNs != 0L) ? endNs : System.nanoTime();
        double sec = (startNs == 0L) ? 0.0 : (end - startNs) / 1_000_000_000.0;

        StringBuilder sb = new StringBuilder();
        sb.append(sep());
        sb.append("DONE | ").append("Input=").append(inputModeCombo.getSelectedItem())
          .append(" | n=").append(baseData == null ? 0 : baseData.length)
          .append(" | speed=").append(speedSlider.getValue()).append("% (").append(calcDelayMs()).append("ms)")
          .append(String.format(" | time=%.2fs\n", sec));

        if (!compareMode) {
            sb.append("Algo=").append(leftName)
              .append(" | steps=").append(stepsL.size())
              .append(" | compares=").append(comparesL)
              .append(" | writes=").append(writesL)
              .append("\n");
        } else {
            sb.append("Left = ").append(leftName)
              .append(" | steps=").append(stepsL.size())
              .append(" | compares=").append(comparesL)
              .append(" | writes=").append(writesL)
              .append("\n");
            sb.append("Right= ").append(rightName)
              .append(" | steps=").append(stepsR.size())
              .append(" | compares=").append(comparesR)
              .append(" | writes=").append(writesR)
              .append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private void appendHistory(String line) {
        historyArea.append(line);
        if (!line.endsWith("\n")) historyArea.append("\n");
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }

    private static String sep() {
        return "----------------------------------------\n";
    }

    private static String blockLine(String head, String body) {
        if (body == null || body.isEmpty()) return head + "\n";
        return head + " : " + body + "\n";
    }

    // ---------------- Parsing ----------------

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

    // ---------------- Chart ----------------

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
