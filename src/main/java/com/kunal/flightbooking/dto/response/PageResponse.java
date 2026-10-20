package com.kunal.flightbooking.dto.response;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record PageResponse<T extends Serializable>(List<T> content, int page, int size, long totalElements,
                                                   int totalPages) implements Serializable {

    public static <T extends Serializable> PageResponse<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (int) Math.ceil(all.size() / (double) size);
        return new PageResponse<>(new ArrayList<>(all.subList(from, to)), page, size, all.size(), totalPages);
    }
}
