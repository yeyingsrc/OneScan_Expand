package burp.vaycore.common.filter;

import burp.vaycore.common.utils.StringUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;

public class TableFilter<T extends AbstractTableModel> extends RowFilter<T, Object> {

    private final FilterRule rule;

    public TableFilter(FilterRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("rule is null");
        }
        if (rule.getItems().isEmpty()) {
            throw new IllegalArgumentException("rule condition is null");
        }
        this.rule = rule;
    }

    public FilterRule getRule() {
        return rule;
    }

    @Override
    public boolean include(Entry<? extends T, ?> entry) {
        T model = entry.getModel();
        Integer rowIndex = (Integer) entry.getIdentifier();
        int columnIndex = rule.getColumnIndex();
        if (columnIndex < 0 || columnIndex >= model.getColumnCount()
                || rowIndex < 0 || rowIndex >= model.getRowCount()) {
            return false;
        }

        Object valueObj = model.getValueAt(rowIndex, columnIndex);
        String value = valueObj == null ? "" : String.valueOf(valueObj);
        ArrayList<FilterRule.Item> items = rule.getItems();
        boolean result = false;
        for (int i = 0; i < items.size(); i++) {
            FilterRule.Item item = items.get(i);
            boolean check = checkRuleItem(value, item);
            if (items.size() == 1) {
                return check;
            }

            int logic = item.getLogic();
            if (logic == 0) {
                result = check;
            } else if (logic == FilterRule.LOGIC_OR) {
                result = result || check;
            } else if (logic == FilterRule.LOGIC_AND) {
                result = result && check;
            }

            int nextIndex = i + 1;
            if (nextIndex < items.size()) {
                int nextLogic = items.get(nextIndex).getLogic();
                if (nextLogic == FilterRule.LOGIC_AND && !result) {
                    return false;
                }
                if (nextLogic == FilterRule.LOGIC_OR && result) {
                    return true;
                }
            }
        }
        return result;
    }

    private boolean checkRuleItem(String value, FilterRule.Item item) {
        int operate = item.getOperate();
        if (operate >= FilterRule.OPERATE_GT && operate <= FilterRule.OPERATE_LT_EQUAL) {
            int left = StringUtils.parseInt(value);
            int right = StringUtils.parseInt(item.getValue());
            return switch (operate) {
                case FilterRule.OPERATE_GT -> left > right;
                case FilterRule.OPERATE_GT_EQUAL -> left >= right;
                case FilterRule.OPERATE_LT -> left < right;
                case FilterRule.OPERATE_LT_EQUAL -> left <= right;
                default -> false;
            };
        }

        String right = item.getValue();
        return switch (operate) {
            case FilterRule.OPERATE_EQUAL -> value.equals(right);
            case FilterRule.OPERATE_NOT_EQUAL -> !value.equals(right);
            case FilterRule.OPERATE_START -> value.startsWith(right);
            case FilterRule.OPERATE_NOT_START -> !value.startsWith(right);
            case FilterRule.OPERATE_END -> value.endsWith(right);
            case FilterRule.OPERATE_NOT_END -> !value.endsWith(right);
            case FilterRule.OPERATE_INCLUDE -> value.contains(right);
            case FilterRule.OPERATE_NOT_INCLUDE -> !value.contains(right);
            default -> false;
        };
    }
}
