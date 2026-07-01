package com.recall.ui;

import com.recall.core.SearchResult;

import javax.swing.*;
import java.util.*;

/**
 * Custom ListModel that supports both SearchResult items and section headers.
 * Section headers are non-selectable dividers.
 */
public class ResultListModel extends AbstractListModel<ResultItem> {

    private List<ResultItem> items = new ArrayList<>();

    public void setResults(List<SearchResult> results) {
        items.clear();

        if (results.isEmpty()) {
            fireContentsChanged(this, 0, 0);
            return;
        }

        // Group results by category
        Map<String, List<SearchResult>> grouped = groupByCategory(results);

        // Add items in order: FILES, CODE, RECENT, other categories
        String[] categoryOrder = {"FILES", "CODE", "RECENT"};

        for (String category : categoryOrder) {
            List<SearchResult> categoryResults = grouped.get(category);
            if (categoryResults != null && !categoryResults.isEmpty()) {
                items.add(ResultItem.fromHeader(category));
                for (SearchResult r : categoryResults) {
                    items.add(ResultItem.fromResult(r));
                }
            }
        }

        fireContentsChanged(this, 0, items.size());
    }

    public void clear() {
        int size = items.size();
        items.clear();
        if (size > 0) {
            fireContentsChanged(this, 0, size);
        }
    }

    @Override
    public int getSize() {
        return items.size();
    }

    @Override
    public ResultItem getElementAt(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    public int getNextSelectableIndex(int fromIndex, int direction) {
        if (direction > 0) {
            // Moving down
            for (int i = fromIndex + 1; i < items.size(); i++) {
                if (!items.get(i).isHeader()) {
                    return i;
                }
            }
        } else {
            // Moving up
            for (int i = fromIndex - 1; i >= 0; i--) {
                if (!items.get(i).isHeader()) {
                    return i;
                }
            }
        }
        return fromIndex;
    }

    public int getFirstSelectableIndex() {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isHeader()) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, List<SearchResult>> groupByCategory(List<SearchResult> results) {
        Map<String, List<SearchResult>> grouped = new LinkedHashMap<>();

        for (SearchResult r : results) {
            String category = categorizeResult(r);
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(r);
        }

        return grouped;
    }

    private String categorizeResult(SearchResult r) {
        String ext = (r.ext() != null) ? r.ext().toLowerCase() : "";

        if (ext.matches("java|py|js|ts|cpp|c|h|go|rs|kt|swift")) {
            return "CODE";
        } else if ("pdf".equals(ext)) {
            return "FILES";
        } else {
            return "FILES";
        }
    }
}

