package br.com.ctcore.api.page;

import java.util.List;

/** Envelope de toda lista: {@code { items, page, size, total }}. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> of(List<T> items, PageRequest request, long total) {
        return new PageResponse<>(items, request.page(), request.size(), total);
    }
}
