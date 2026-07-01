package com.recall.ui;

import com.recall.core.SearchResult;

/**
 * Wrapper for items in the ResultListModel.
 * Can be either a SearchResult or a section header string.
 */
public class ResultItem {
    private SearchResult result;
    private String sectionHeader;
    private boolean isHeader;

    private ResultItem() {}

    public static ResultItem fromResult(SearchResult result) {
        ResultItem item = new ResultItem();
        item.result = result;
        item.isHeader = false;
        return item;
    }

    public static ResultItem fromHeader(String headerText) {
        ResultItem item = new ResultItem();
        item.sectionHeader = headerText;
        item.isHeader = true;
        return item;
    }

    public SearchResult getResult() {
        return result;
    }

    public String getHeaderText() {
        return sectionHeader;
    }

    public boolean isHeader() {
        return isHeader;
    }

    public int getHeight() {
        return isHeader ? 22 : 42;
    }
}

