package com.nxh.redis.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;

@Slf4j
public class PagingUtil {

    public static Pageable buildPageable(int page, int size, List<String> sorts) throws Exception {
        Pageable pageRequest;
        Sort sort = SortUtils.buildSort(sorts);
        if (Objects.nonNull(sort)) {
            pageRequest = PageRequest.of(page, size, sort);
        } else {
            pageRequest = PageRequest.of(page, size);
        }

        return pageRequest;
    }

    public static Sort buildSort(List<String> sorts) {
        Sort sort = SortUtils.buildSort(sorts);
        if (Objects.nonNull(sort)) {
            return sort;
        }

        return Sort.unsorted();
    }
}