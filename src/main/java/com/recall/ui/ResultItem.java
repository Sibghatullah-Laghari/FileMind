package com.recall.ui;

import com.recall.core.SearchResult;

/**
 * Wrapper for items in the ResultListModel.
 * Can be either a SearchResult or a section header string.
 *
 * This class is used to unify search results and section headers
 * in a single list model for JList or similar components.
 */
public class ResultItem {
    private SearchResult result;
    private String sectionHeader;
    private boolean isHeader;

    /** Private constructor; use static factory methods. */
    private ResultItem() {}

    /**
     * Creates a ResultItem representing a search result.
     *
     * @param result the SearchResult to wrap
     * @return a new ResultItem instance with isHeader = false
     */
    public static ResultItem fromResult(SearchResult result) {
        ResultItem item = new ResultItem();
        item.result = result;
        item.isHeader = false;
        return item;
    }

    /**
     * Creates a ResultItem representing a section header.
     *
     * @param headerText the text to display as the header
     * @return a new ResultItem instance with isHeader = true
     */
    public static ResultItem fromHeader(String headerText) {
        ResultItem item = new ResultItem();
        item.sectionHeader = headerText;
        item.isHeader = true;
        return item;
    }

    /**
     * Returns the wrapped SearchResult, or null if this is a header.
     *
     * @return the SearchResult instance, or null
     */
    public SearchResult getResult() {
        return result;
    }

    /**
     * Returns the header text, or null if this is not a header.
     *
     * @return the header text, or null
     */
    public String getHeaderText() {
        return sectionHeader;
    }

    /**
     * Checks if this item represents a section header.
     *
     * @return true if this is a header, false otherwise
     */
    public boolean isHeader() {
        return isHeader;
    }

    /**
     * Returns the fixed height (in pixels) for this item in the list.
     * Headers are shorter (22px) than results (42px).
     *
     * @return the height for this item
     *
     * FIXME: Hardcoded pixel values assume a specific UI design;
     *        should be defined as constants or derived from font metrics.
     * FIXME: The height should be dynamic based on font size or user scaling.
     */
    public int getHeight() {
        return isHeader ? 22 : 42;
    }
}