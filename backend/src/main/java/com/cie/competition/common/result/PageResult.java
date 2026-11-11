package com.cie.competition.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页响应对象，供所有分页查询接口复用。
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
