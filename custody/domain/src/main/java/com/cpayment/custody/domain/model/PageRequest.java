package com.cpayment.custody.domain.model;

public record PageRequest(int pageNumber, int pageSize) {

    public PageRequest {
        if (pageNumber < 0) throw new IllegalArgumentException("pageNumber >= 0");
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize 1..500");
    }

    public static PageRequest of(int page, int size) { return new PageRequest(page, size); }
}
