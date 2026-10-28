package com.example.adventurebook.service;

import com.example.adventurebook.entity.book.Consequence;

/**
 * Calculates the health change caused by a selected option's consequence.
 */
public interface ConsequenceStrategy {

    /**
     * Calculates a signed health change without clamping the resulting health.
     * @param consequence option consequence; null means no effect
     * @return a negative value for damage, a positive value for healing, or zero
     */
    int calculateHealthDelta(Consequence consequence);
}
