package com.cpayment.custody.domain.model;

import java.util.List;

public record Page<T>(List<T> items, int pageNumber, int pageSize, long totalElements) {

    public boolean hasNext() { return (long) (pageNumber + 1) * pageSize < totalElements; }
}
