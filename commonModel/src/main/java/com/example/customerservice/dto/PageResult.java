package com.example.customerservice.dto;

import java.io.Serializable;
import java.util.List;

/** 统一分页返回对象。 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long pageNo;
    private long pageSize;
    private long total;
    private long pages;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long pageNo, long pageSize, long total, long pages, List<T> records) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.pages = pages;
        this.records = records;
    }

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
