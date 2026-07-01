package com.recall.ui;

import com.recall.core.LuceneIndexer;
import com.recall.core.MetadataDB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Settings dialog for FileMind.
 * 500x400 JDialog with General, Indexing, Storage, and About sections.
 */
public class SettingsDialog extends JDialog {

    private static final int DIALOG_W = 500;
    private static final int DIALOG_H = 400;

    private JSlider maxFileSizeSlider;
    private JTextField hotkeyField;
    private JComboBox<String> themeCombo;
    private DefaultListModel<String> folderListModel;
    private JList<String> folderList;
    private JProgressBar reindexProgress;
    private JLabel indexSizeLabel;

    public SettingsDialog(Window owner) {
        super(owner, "Settings", ModalityType.APPLICATION_MODAL);
        setSize(DIALOG_W, DIALOG_H);
        setLocationRelativeTo(owner);
        setResizable(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        tabs.addTab("General", buildGeneralPanel());
        tabs.addTab("Indexing", buildIndexingPanel());
        tabs.addTab("Storage", buildStoragePanel());
        tabs.addTab("About", buildAboutPanel());

        add(tabs);

        // Apply theme
        getContentPane().setBackground(ThemeManager.getPanelBg());
    }

    private JPanel buildGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(ThemeManager.getPanelBg());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 6, 6, 6);

        // Theme
        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel("Theme:"), c);
        c.gridx = 1;
        themeCombo = new JComboBox<>(new String[]{"Dark", "Light"});
        themeCombo.setSelectedItem(ThemeManager.isDark() ? "Dark" : "Light");
        themeCombo.addActionListener(e -> {
            boolean dark = "Dark".equals(themeCombo.getSelectedItem());
            if (dark != ThemeManager.isDark()) {
                ThemeManager.toggleTheme();
                SwingUtilities.getWindowAncestor(panel).dispose();
            }
        });
        panel.add(themeCombo, c);

        // Hotkey
        c.gridx = 0; c.gridy = 1;
        panel.add(new JLabel("Hotkey:"), c);
        c.gridx = 1;
        hotkeyField = new JTextField(15);
        hotkeyField.setText("Ctrl+Shift+F");
        hotkeyField.setToolTipText("Format: ctrl+shift+<key>");
        panel.add(hotkeyField, c);

        // Reset floating icon position
        c.gridx = 0; c.gridy = 2;
        c.gridwidth = 2;
        JButton resetPosBtn = new JButton("Reset floating icon position");
        resetPosBtn.addActionListener(e -> {
            try {
                Path posFile = Paths.get(System.getProperty("user.home"), ".filemind", "icon_pos.conf");
                Files.deleteIfExists(posFile);
                JOptionPane.showMessageDialog(this,
                        "Floating icon position reset. Restart to apply.",
                        "Reset", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ignored) {}
        });
        panel.add(resetPosBtn, c);

        return panel;
    }

    private JPanel buildIndexingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(ThemeManager.getPanelBg());

        // Watched folders
        JLabel folderLabel = new JLabel("Watched folders:");
        folderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        folderListModel = new DefaultListModel<>();
        addDefaultFolders();
        folderList = new JList<>(folderListModel);
        folderList.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JScrollPane folderScroll = new JScrollPane(folderList);
        folderScroll.setPreferredSize(new Dimension(0, 120));

        JPanel folderBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        folderBtnPanel.setBackground(ThemeManager.getPanelBg());

        JButton addBtn = new JButton("+ Add");
        addBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!folderListModel.contains(path)) {
                    folderListModel.addElement(path);
                }
            }
        });
        folderBtnPanel.add(addBtn);

        JButton removeBtn = new JButton("\u2212 Remove");
        removeBtn.addActionListener(e -> {
            int idx = folderList.getSelectedIndex();
            if (idx >= 0) folderListModel.remove(idx);
        });
        folderBtnPanel.add(removeBtn);

        JPanel folderPanel = new JPanel(new BorderLayout(6, 6));
        folderPanel.setBackground(ThemeManager.getPanelBg());
        folderPanel.add(folderLabel, BorderLayout.NORTH);
        folderPanel.add(folderScroll, BorderLayout.CENTER);
        folderPanel.add(folderBtnPanel, BorderLayout.SOUTH);

        // Max file size slider
        JPanel sliderPanel = new JPanel(new BorderLayout(6, 6));
        sliderPanel.setBackground(ThemeManager.getPanelBg());
        sliderPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel sliderLabel = new JLabel("Max file size:");
        sliderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel sliderRow = new JPanel(new BorderLayout(6, 0));
        sliderRow.setBackground(ThemeManager.getPanelBg());
        maxFileSizeSlider = new JSlider(JSlider.HORIZONTAL, 1, 500, 50);
        maxFileSizeSlider.setMajorTickSpacing(100);
        maxFileSizeSlider.setPaintTicks(true);
        maxFileSizeSlider.setPaintLabels(true);
        JLabel sliderVal = new JLabel("50 MB");
        maxFileSizeSlider.addChangeListener(e ->
            sliderVal.setText(maxFileSizeSlider.getValue() + " MB")
        );
        sliderRow.add(maxFileSizeSlider, BorderLayout.CENTER);
        sliderRow.add(sliderVal, BorderLayout.EAST);

        sliderPanel.add(sliderLabel, BorderLayout.NORTH);
        sliderPanel.add(sliderRow, BorderLayout.CENTER);

        // Re-index button
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 10));
        btnPanel.setBackground(ThemeManager.getPanelBg());

        JButton reindexBtn = new JButton("Re-index now");
        reindexProgress = new JProgressBar(0, 100);
        reindexProgress.setPreferredSize(new Dimension(200, 20));
        reindexProgress.setVisible(false);

        reindexBtn.addActionListener(e -> {
            reindexProgress.setVisible(true);
            reindexProgress.setIndeterminate(true);
            reindexBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    Path indexDir = Paths.get(System.getProperty("user.home"), ".filemind", "index");
                    LuceneIndexer.init(indexDir);
                    for (int i = 0; i < folderListModel.size(); i++) {
                        Path p = Paths.get(folderListModel.get(i));
                        if (Files.exists(p)) {
                            LuceneIndexer.indexFolder(p, null);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("[REINDEX] " + ex.getMessage());
                }
                SwingUtilities.invokeLater(() -> {
                    reindexProgress.setIndeterminate(false);
                    reindexProgress.setValue(100);
                    reindexBtn.setEnabled(true);
                });
            }).start();
        });
        btnPanel.add(reindexBtn);
        btnPanel.add(reindexProgress);

        panel.add(folderPanel, BorderLayout.NORTH);
        panel.add(sliderPanel, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void addDefaultFolders() {
        String home = System.getProperty("user.home");
        String[] defaults = {home + "/Documents", home + "/Downloads", home + "/Desktop", home + "/Projects"};
        for (String d : defaults) {
            if (Files.exists(Paths.get(d))) {
                folderListModel.addElement(d);
            }
        }
    }

    private JPanel buildStoragePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(ThemeManager.getPanelBg());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 8, 8, 8);
        c.gridwidth = 2;

        // Current index size
        JLabel sizeTitle = new JLabel("Current index size:");
        sizeTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        c.gridx = 0; c.gridy = 0;
        panel.add(sizeTitle, c);

        indexSizeLabel = new JLabel(calculateIndexSize());
        indexSizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        c.gridy = 1;
        panel.add(indexSizeLabel, c);

        // Clear & Re-index
        c.gridy = 2;
        c.insets = new Insets(20, 8, 8, 8);
        JButton clearBtn = new JButton("Clear and Re-index");
        clearBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Clear all indexes and re-index from scratch?\nThis may take a while.",
                    "Confirm Re-index", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                new Thread(() -> {
                    try {
                        Path indexDir = Paths.get(System.getProperty("user.home"), ".filemind", "index");
                        LuceneIndexer.close();
                        deleteDirectory(indexDir.toFile());
                        LuceneIndexer.init(indexDir);
                        SwingUtilities.invokeLater(() -> {
                            indexSizeLabel.setText(calculateIndexSize());
                            JOptionPane.showMessageDialog(this,
                                    "Index cleared. Restart to re-index.",
                                    "Done", JOptionPane.INFORMATION_MESSAGE);
                        });
                    } catch (Exception ex) {
                        System.err.println("[CLEAR] " + ex.getMessage());
                    }
                }).start();
            }
        });
        panel.add(clearBtn, c);

        return panel;
    }

    private String calculateIndexSize() {
        Path indexDir = Paths.get(System.getProperty("user.home"), ".filemind", "index");
        if (!Files.exists(indexDir)) return "0 B";
        try {
            long size = Files.walk(indexDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    })
                    .sum();
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return (size / 1024) + " KB";
            return (size / (1024 * 1024)) + " MB";
        } catch (IOException e) {
            return "?";
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private JPanel buildAboutPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(ThemeManager.getPanelBg());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 8, 4, 8);
        c.gridwidth = 1;

        String[][] info = {
                {"FileMind", "v1.0"},
                {"Java", System.getProperty("java.version")},
                {"Lucene", "9"},
                {"Tika", "2.9.1"},
                {"SQLite", ""},
                {"Index", "~/.filemind/index"}
        };

        for (int i = 0; i < info.length; i++) {
            c.gridx = 0; c.gridy = i;
            JLabel keyLabel = new JLabel(info[i][0] + ":");
            keyLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            panel.add(keyLabel, c);
            c.gridx = 1;
            JLabel valLabel = new JLabel(info[i][1]);
            valLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            valLabel.setForeground(ThemeManager.getTextSecondary());
            panel.add(valLabel, c);
        }

        return panel;
    }
}
