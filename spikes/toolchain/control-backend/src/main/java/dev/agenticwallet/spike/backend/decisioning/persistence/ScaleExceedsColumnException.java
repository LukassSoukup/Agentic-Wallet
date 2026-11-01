package dev.agenticwallet.spike.backend.decisioning.persistence;

import java.math.BigDecimal;

/** A decimal with more scale than its column would be rounded silently by PostgreSQL; refused. */
final class ScaleExceedsColumnException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    ScaleExceedsColumnException(String column, BigDecimal value, int columnScale) {
        super(
                "column "
                        + column
                        + " has scale "
                        + columnScale
                        + " but the value has scale "
                        + value.scale());
    }
}
