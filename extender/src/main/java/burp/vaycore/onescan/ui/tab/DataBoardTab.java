package burp.vaycore.onescan.ui.tab;

import burp.vaycore.common.filter.FilterRule;
import burp.vaycore.common.filter.TableFilter;
import burp.vaycore.common.filter.TableFilterPanel;
import burp.vaycore.common.helper.UIHelper;
import burp.vaycore.common.layout.HLayout;
import burp.vaycore.common.layout.VLayout;
import burp.vaycore.common.utils.IPUtils;
import burp.vaycore.common.utils.Utils;
import burp.vaycore.common.widget.HintTextField;
import burp.vaycore.onescan.bean.FpData;
import burp.vaycore.onescan.bean.TaskData;
import burp.vaycore.onescan.common.Config;
import burp.vaycore.onescan.common.DialogCallbackAdapter;
import burp.vaycore.onescan.common.L;
import burp.vaycore.onescan.common.OnFpColumnModifyListener;
import burp.vaycore.onescan.manager.FpManager;
import burp.vaycore.onescan.manager.TaskPersistenceManager;
import burp.vaycore.onescan.ui.base.BaseTab;
import burp.vaycore.onescan.ui.widget.DividerLine;
import burp.vaycore.onescan.ui.widget.ImportUrlWindow;
import burp.vaycore.onescan.ui.widget.TaskFieldSelectionPanel;
import burp.vaycore.onescan.ui.widget.TaskTable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据看板
 * <p>
 * Created by vaycore on 2022-08-07.
 */
public class DataBoardTab extends BaseTab implements ImportUrlWindow.OnImportUrlListener, OnFpColumnModifyListener, ActionListener {

    public static final String EVENT_IMPORT_URL = "event-import-url";
    public static final String EVENT_STOP_TASK = "event-stop-task";

    private TaskTable mTaskTable;
    private JCheckBox mListenProxyMessage;
    private JCheckBox mRemoveHeader;
    private JCheckBox mReplaceHeader;
    private JCheckBox mDirScan;
    private ArrayList<FilterRule> mLastFilters;
    private HintTextField mFilterRuleText;
    private JCheckBox mPayloadProcessing;
    private ImportUrlWindow mImportUrlWindow;
    private JLabel mTaskStatus;
    private JLabel mLFTaskStatus;
    private JLabel mFpCacheStatus;
    private JLabel mTaskHistoryStatus;
    private Timer mAutoSaveTimer;
    private long mLastAutoSavedVersion = -1L;
    private final ExecutorService mSaveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OneScan-data-save");
        thread.setDaemon(true);
        return thread;
    });
    private long mLastAutoSaveQueuedVersion = -1L;

    @Override
    protected void initData() {
        FpManager.addOnFpColumnModifyListener(this);
    }

    @Override
    protected void initView() {
    }

    public String getTitleName() {
        return L.get("tab_name.databoard");
    }

    public void testInit() {
        init(new JTextArea(L.get("request")), new JTextArea(L.get("response")));
        // 添加测试数据
        for (int i = 0; i < 100; i++) {
            TaskData data = new TaskData();
            data.setMethod(i % 12 == 0 ? "POST" : "GET");
            data.setHost("https://www.baidu.com");
            data.setUrl("/?s=" + i);
            data.setTitle("百度一下，你就知道");
            data.setIp(IPUtils.randomIPv4());
            data.setStatus(200);
            data.setLength(Utils.randomInt(99999));
            FpData fp = Utils.getRandomItem(FpManager.getList());
            if (fp != null) {
                List<FpData> list = new ArrayList<>();
                list.add(fp);
                data.setFingerprint(list);
            }
            data.setFrom("Proxy");
            data.setReqResp(new Object());
            getTaskTable().addTaskData(data);
        }
    }

    public void init(Component requestTextEditor, Component responseTextEditor) {
        if (requestTextEditor == null || responseTextEditor == null) {
            return;
        }
        setLayout(new VLayout(0));
        // 初始化控制栏
        initControlPanel();
        // 主面板
        JSplitPane mainSplitPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPanel.setResizeWeight(0.55D);
        mainSplitPanel.setDividerSize(3);
        // 请求列表
        mTaskTable = new TaskTable();
        JScrollPane scrollPane = new JScrollPane(mTaskTable);
        scrollPane.setPreferredSize(new Dimension(scrollPane.getWidth(), 0));
        // 请求和响应面板
        JSplitPane dataSplitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        dataSplitPanel.setResizeWeight(0.5D);
        dataSplitPanel.setDividerSize(3);
        dataSplitPanel.add(requestTextEditor, JSplitPane.LEFT);
        dataSplitPanel.add(responseTextEditor, JSplitPane.RIGHT);
        // 添加子面板控件
        mainSplitPanel.add(scrollPane, JSplitPane.LEFT);
        mainSplitPanel.add(dataSplitPanel, JSplitPane.RIGHT);
        // 将布局进行展示
        add(mainSplitPanel, "1w");
        // 状态栏
        initStatusPanel();
        // 加载过滤规则
        loadFilterRules();
    }

    /**
     * 初始化控制栏
     */
    private void initControlPanel() {
        JPanel panel = new JPanel(new HLayout(5, true));
        panel.setBorder(new EmptyBorder(0, 0, 0, 5));
        panel.setFocusable(false);
        add(panel);
        // 代理监听开关
        mListenProxyMessage = newJCheckBox(panel, L.get("listen_proxy"), Config.KEY_ENABLE_LISTEN_PROXY);
        // 请求头移除开关
        mRemoveHeader = newJCheckBox(panel, L.get("remove_header"), Config.KEY_ENABLE_REMOVE_HEADER);
        // 请求头替换开关
        mReplaceHeader = newJCheckBox(panel, L.get("replace_header"), Config.KEY_ENABLE_REPLACE_HEADER);
        // 递归扫描开关
        mDirScan = newJCheckBox(panel, L.get("dir_scan"), Config.KEY_ENABLE_DIR_SCAN);
        // 请求包处理开关
        mPayloadProcessing = newJCheckBox(panel, L.get("databoard_payload_processing"),
                Config.KEY_ENABLE_PAYLOAD_PROCESSING);
        // 导入Url
        JButton importUrlBtn = new JButton(L.get("import_url"));
        importUrlBtn.setToolTipText(L.get("import_url"));
        importUrlBtn.setActionCommand("import-url");
        importUrlBtn.addActionListener(this);
        panel.add(importUrlBtn);
        // 停止按钮
        JButton stopBtn = new JButton(L.get("stop"));
        stopBtn.setToolTipText(L.get("stop_all_task"));
        stopBtn.setActionCommand("stop-task");
        stopBtn.addActionListener(this);
        panel.add(stopBtn);
        // 清空历史记录按钮
        JButton clearBtn = new JButton(L.get("clear_record"));
        clearBtn.setToolTipText(L.get("clear_history"));
        clearBtn.setActionCommand("clear-history");
        clearBtn.addActionListener(this);
        panel.add(clearBtn);
        JButton dataProcessingBtn = new JButton(L.get("data_processing"));
        dataProcessingBtn.setToolTipText(L.get("data_processing"));
        dataProcessingBtn.addActionListener(e -> showDataProcessingMenu(dataProcessingBtn));
        panel.add(dataProcessingBtn);
        // 撑开布局
        panel.add(new JPanel(), "1w");
        // 过滤设置
        mFilterRuleText = new HintTextField();
        mFilterRuleText.setEditable(false);
        mFilterRuleText.setHintText(L.get("no_filter_rules"));
        panel.add(mFilterRuleText, "2w");
        JButton filterBtn = new JButton(L.get("filter"));
        filterBtn.setToolTipText(L.get("filter_data"));
        filterBtn.setActionCommand("filter-data");
        filterBtn.addActionListener(this);
        panel.add(filterBtn, "65px");
    }

    /**
     * 初始化状态栏
     */
    private void initStatusPanel() {
        add(DividerLine.h());
        JPanel panel = new JPanel(new HLayout(10));
        panel.setBorder(new EmptyBorder(5, 10, 5, 10));
        panel.setFocusable(false);
        panel.add(new JPanel(), "1w");
        add(panel);
        // 添加状态信息组件
        mTaskStatus = addStatusInfoPanel(panel);
        mLFTaskStatus = addStatusInfoPanel(panel);
        mTaskHistoryStatus = addStatusInfoPanel(panel);
        mFpCacheStatus = addStatusInfoPanel(panel);
        // 刷新默认显示的信息
        refreshTaskStatus(0, 0);
        refreshLFTaskStatus(0, 0);
        refreshTaskHistoryStatus();
        refreshFpCacheStatus();
    }

    /**
     * 添加状态信息组件
     *
     * @param panel 状态栏布局
     * @return 返回 JLabel 组件
     */
    private JLabel addStatusInfoPanel(JPanel panel) {
        // 分隔线
        panel.add(DividerLine.v());
        // 显示的内容
        JLabel label = new JLabel();
        panel.add(label);
        return label;
    }

    /**
     * 从配置文件中加载过滤规则
     */
    private void loadFilterRules() {
        ArrayList<FilterRule> rules = Config.getDataboardFilterRules();
        if (rules == null) {
            return;
        }
        // 借助 TableFilterPanel 组件转换配置
        TableFilterPanel panel = new TableFilterPanel(TaskTable.getColumnNames(), rules);
        ArrayList<TableFilter<AbstractTableModel>> filters = panel.exportTableFilters();
        String rulesText = panel.exportRulesText();
        mTaskTable.setRowFilter(filters);
        mFilterRuleText.setText(rulesText);
        mLastFilters = rules;
    }

    /**
     * 创建开关组件
     *
     * @param panel     控制栏布局
     * @param text      开关标签
     * @param configKey 要绑定的配置 key
     * @return 开关组件实例
     */
    private JCheckBox newJCheckBox(JPanel panel, String text, String configKey) {
        JCheckBox checkBox = new JCheckBox(text, Config.getBoolean(configKey));
        checkBox.setFocusable(false);
        checkBox.setMargin(new Insets(5, 5, 5, 5));
        panel.add(checkBox);
        checkBox.addActionListener(e -> {
            boolean configSelected = Config.getBoolean(configKey);
            boolean selected = checkBox.isSelected();
            if (selected == configSelected) {
                return;
            }
            // 保存配置
            Config.put(configKey, String.valueOf(selected));
        });
        return checkBox;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "import-url":
                importUrl();
                break;
            case "stop-task":
                stopTask();
                break;
            case "clear-history":
                clearHistory();
                break;
            case "filter-data":
                showSetupFilterDialog();
                break;
            case "data-processing-menu":
                showDataProcessingMenu((Component) e.getSource());
                break;
            case "export-stored-data":
                exportStoredData();
                break;
            case "import-stored-data":
                importStoredData();
                break;
            case "save-stored-data":
                saveStoredData(true);
                break;
        }
    }

    private void showDataProcessingMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        addDataProcessingMenuItem(menu, L.get("save"), L.get("save_stored_data"), "save-stored-data");
        addDataProcessingMenuItem(menu, L.get("import_data_short"), L.get("import_stored_data"), "import-stored-data");
        addDataProcessingMenuItem(menu, L.get("export_data_short"), L.get("export_stored_data"), "export-stored-data");
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void addDataProcessingMenuItem(JPopupMenu menu, String text, String tooltip, String actionCommand) {
        JMenuItem item = new JMenuItem(text);
        item.setToolTipText(tooltip);
        item.setActionCommand(actionCommand);
        item.addActionListener(this);
        menu.add(item);
    }

    /**
     * 显示导入 URL 窗口
     */
    private void importUrl() {
        if (mImportUrlWindow == null) {
            mImportUrlWindow = new ImportUrlWindow();
            mImportUrlWindow.setOnImportUrlListener(this);
        }
        mImportUrlWindow.showWindow();
    }

    /**
     * 关闭导入 URL 窗口
     */
    public void closeImportUrlWindow() {
        if (mImportUrlWindow != null) {
            mImportUrlWindow.closeWindow();
        }
    }

    @Override
    public void onImportUrl(List<String> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        sendTabEvent(EVENT_IMPORT_URL, data);
    }

    @Override
    public void onFpColumnModify() {
        if (mTaskTable != null) {
            mTaskTable.refreshColumns();
        }
    }

    /**
     * 停止扫描任务
     */
    private void stopTask() {
        // 停止任务前，优先将代理监听开关关闭
        mListenProxyMessage.setSelected(false);
        // 发送事件消息
        sendTabEvent(EVENT_STOP_TASK);
        // 通知停止添加任务数据
        if (mTaskTable != null) {
            mTaskTable.stopAddTaskData();
        }
    }

    /**
     * 清空历史记录
     */
    private void clearHistory() {
        if (mTaskTable == null) {
            return;
        }
        mTaskTable.clearAll();
        refreshTaskHistoryStatus();
    }

    private void saveStoredData(boolean showTips) {
        if (mTaskTable == null) {
            return;
        }
        try {
            TaskTable.TaskSnapshot snapshot = mTaskTable.getTaskSnapshot();
            if (!showTips && snapshot.version() == mLastAutoSavedVersion) {
                return;
            }
            if (!showTips && snapshot.version() == mLastAutoSaveQueuedVersion) {
                return;
            }
            List<TaskData> items = snapshot.items();
            if (items.isEmpty()) {
                if (showTips) {
                    UIHelper.showTipsDialog(L.get("data_persistence_no_current_data"));
                }
                return;
            }
            if (!showTips) {
                mLastAutoSaveQueuedVersion = snapshot.version();
            }
            Runnable task = () -> {
                try {
                    TaskPersistenceManager.SaveResult result = TaskPersistenceManager.persistSnapshot(items);
                    if (showTips) {
                        SwingUtilities.invokeLater(() -> {
                            if (result.count() <= 0) {
                                UIHelper.showTipsDialog(L.get("data_persistence_disabled_hint"));
                            } else {
                                UIHelper.showTipsDialog(L.get("save_stored_data_success", result.label(), result.count()));
                            }
                        });
                    } else {
                        mLastAutoSavedVersion = snapshot.version();
                        mLastAutoSaveQueuedVersion = snapshot.version();
                    }
                } catch (Exception ex) {
                    if (!showTips) {
                        mLastAutoSaveQueuedVersion = mLastAutoSavedVersion;
                    }
                    if (showTips) {
                        SwingUtilities.invokeLater(() -> UIHelper.showTipsDialog(L.get("error_hint", ex.getMessage())));
                    }
                }
            };
            mSaveExecutor.execute(task);
        } catch (Exception ex) {
            if (showTips) {
                UIHelper.showTipsDialog(L.get("error_hint", ex.getMessage()));
            }
        }
    }

    private void exportStoredData() {
        TaskFieldSelectionPanel fieldPanel = new TaskFieldSelectionPanel(TaskPersistenceManager.getConfiguredFieldKeys());
        JScrollPane scrollPane = new JScrollPane(fieldPanel);
        scrollPane.setPreferredSize(new Dimension(420, 220));
        int ret = JOptionPane.showConfirmDialog(this, scrollPane, L.get("export_stored_data"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ret != JOptionPane.OK_OPTION) {
            return;
        }
        String defaultPath = new File(Config.getWorkDir(), "onescan-export.csv").getAbsolutePath();
        String selectedPath = UIHelper.selectFileDialog(L.get("select_a_file"), defaultPath);
        if (selectedPath == null || selectedPath.isEmpty()) {
            return;
        }
        try {
            int count = TaskPersistenceManager.exportCsv(new File(selectedPath), fieldPanel.getSelectedFieldKeys());
            UIHelper.showTipsDialog(L.get("export_stored_data_success", count, selectedPath));
        } catch (Exception ex) {
            UIHelper.showTipsDialog(L.get("error_hint", ex.getMessage()));
        }
    }

    private void importStoredData() {
        try {
            List<TaskPersistenceManager.HistoryLabel> labels = TaskPersistenceManager.listLabels();
            if (labels.isEmpty()) {
                UIHelper.showTipsDialog(L.get("data_persistence_no_history"));
                return;
            }
            JComboBox<TaskPersistenceManager.HistoryLabel> labelBox = new JComboBox<>(
                    labels.toArray(new TaskPersistenceManager.HistoryLabel[0]));
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.add(new JLabel(L.get("select_data_label")), BorderLayout.NORTH);
            panel.add(labelBox, BorderLayout.CENTER);
            int ret = JOptionPane.showConfirmDialog(this, panel, L.get("import_stored_data"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ret != JOptionPane.OK_OPTION) {
                return;
            }
            TaskPersistenceManager.HistoryLabel selected =
                    (TaskPersistenceManager.HistoryLabel) labelBox.getSelectedItem();
            if (selected == null) {
                return;
            }
            List<TaskData> items = TaskPersistenceManager.loadTaskDataByLabel(selected.label());
            mTaskTable.loadTaskData(items);
            refreshTaskHistoryStatus();
            UIHelper.showTipsDialog(L.get("import_stored_data_success", selected.label(), items.size()));
        } catch (Exception ex) {
            UIHelper.showTipsDialog(L.get("error_hint", ex.getMessage()));
        }
    }

    /**
     * 获取任务表格组件
     */
    public TaskTable getTaskTable() {
        return mTaskTable;
    }

    /**
     * 是否开启监听代理请求开关
     */
    public boolean hasListenProxyMessage() {
        return mListenProxyMessage != null && mListenProxyMessage.isSelected();
    }

    /**
     * 是否开启移除请求头开关
     */
    public boolean hasRemoveHeader() {
        return mRemoveHeader != null && mRemoveHeader.isSelected();
    }

    /**
     * 是否开启替换请求头开关
     */
    public boolean hasReplaceHeader() {
        return mReplaceHeader != null && mReplaceHeader.isSelected();
    }

    /**
     * 是否开启目录扫描开关
     */
    public boolean hasDirScan() {
        return mDirScan != null && mDirScan.isSelected();
    }

    /**
     * 是否开启请求包处理开关
     */
    public boolean hasPayloadProcessing() {
        return mPayloadProcessing != null && mPayloadProcessing.isSelected();
    }

    /**
     * 刷新任务状态
     *
     * @param over   任务完成数量
     * @param commit 任务提交数量
     */
    public void refreshTaskStatus(int over, int commit) {
        if (mTaskTable == null) {
            return;
        }
        String message = L.get("status_bar_task", over, commit);
        mTaskStatus.setText(message);
    }

    /**
     * 刷新低频任务状态
     *
     * @param over   任务完成数量
     * @param commit 任务提交数量
     */
    public void refreshLFTaskStatus(int over, int commit) {
        if (mLFTaskStatus == null) {
            return;
        }
        String message = L.get("status_bar_low_frequency_task", over, commit);
        mLFTaskStatus.setText(message);
    }

    /**
     * 刷新任务状态
     */
    public void refreshTaskHistoryStatus() {
        if (mTaskHistoryStatus == null || mTaskTable == null) {
            return;
        }
        int count = mTaskTable.getTaskCount();
        String message = L.get("status_bar_task_history", count);
        mTaskHistoryStatus.setText(message);
    }

    /**
     * 刷新指纹缓存状态
     */
    public void refreshFpCacheStatus() {
        if (mFpCacheStatus == null) {
            return;
        }
        String message = L.get("status_bar_fingerprint_cache", FpManager.getCacheCount());
        mFpCacheStatus.setText(message);
    }

    public void refreshAutoSaveTimer() {
        int interval = TaskPersistenceManager.getAutoSaveIntervalSeconds();
        if (!TaskPersistenceManager.isEnabled() || interval <= 0) {
            stopAutoSaveTimer();
            return;
        }
        int delay = interval * 1000;
        if (mAutoSaveTimer != null && mAutoSaveTimer.getDelay() == delay) {
            if (!mAutoSaveTimer.isRunning()) {
                mAutoSaveTimer.start();
            }
            return;
        }
        stopAutoSaveTimer();
        mAutoSaveTimer = new Timer(delay, e -> saveStoredData(false));
        mAutoSaveTimer.setInitialDelay(delay);
        mAutoSaveTimer.start();
    }

    public void closeAutoSaveTimer() {
        stopAutoSaveTimer();
    }

    private void stopAutoSaveTimer() {
        if (mAutoSaveTimer != null) {
            mAutoSaveTimer.stop();
            mAutoSaveTimer = null;
        }
    }

    public void closeDataSaveExecutor() {
        mSaveExecutor.shutdownNow();
    }

    /**
     * 设置过滤对话框
     */
    private void showSetupFilterDialog() {
        TableFilterPanel panel = new TableFilterPanel(TaskTable.getColumnNames(), mLastFilters);
        panel.showDialog(new DialogCallbackAdapter() {

            @Override
            public void onConfirm(ArrayList<FilterRule> filterRules, ArrayList<TableFilter<AbstractTableModel>> filters, String rulesText) {
                mTaskTable.setRowFilter(filters);
                mFilterRuleText.setText(rulesText);
                mLastFilters = filterRules;
                Config.put(Config.KEY_DATABOARD_FILTER_RULES, filterRules);
            }

            @Override
            public void onReset() {
                mTaskTable.setRowFilter(null);
                mFilterRuleText.setText("");
                mLastFilters = null;
                Config.put(Config.KEY_DATABOARD_FILTER_RULES, new ArrayList<>());
            }
        });
    }
}
