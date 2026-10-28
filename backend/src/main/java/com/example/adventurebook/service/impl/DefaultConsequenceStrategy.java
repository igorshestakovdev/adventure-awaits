package com.example.adventurebook.service.impl;

import com.example.adventurebook.entity.book.Consequence;
import com.example.adventurebook.enums.ConsequenceType;
import com.example.adventurebook.service.ConsequenceStrategy;

import org.springframework.stereotype.Service;

/**
 * Converts health loss or gain into a signed health change.
 * The game service is responsible for clamping the resulting health.
 */
@Service
public class DefaultConsequenceStrategy implements ConsequenceStrategy {

    /**
     * Returns zero without a consequence, a negative value for damage, and a positive value for healing.
     */
    @Override
    public int calculateHealthDelta(Consequence consequence) {

        if (consequence == null) {
            return 0;
        }

        return consequence.getType() == ConsequenceType.LOSE_HEALTH
            ? -consequence.getValue()
            : consequence.getValue();
    }
}
