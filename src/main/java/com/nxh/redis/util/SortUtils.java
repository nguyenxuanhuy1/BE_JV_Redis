package com.nxh.redis.util;

import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class SortUtils {
    public static Sort buildSort(List<String> sorts) {
        Sort sort = null;
        if (!CollectionUtils.isEmpty(sorts)) {
            for (String str : sorts) {
                String[] array = str.trim().split("\\s*:\\s*");
                Sort.Direction direction = Sort.Direction.fromString(array[1].toUpperCase());
                if (sort == null) {
                    sort = Sort.by(new Sort.Order(direction, array[0]));
                } else {
                    sort.and(direction == Sort.Direction.ASC ? Sort.by(array[0]).ascending() : Sort.by(array[0]).descending());
                }
            }
        }

        return sort;
    }
}