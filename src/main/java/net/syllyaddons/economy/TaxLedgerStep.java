package net.syllyaddons.economy;

import net.syllyaddons.routing.RuleConfidence;

public record TaxLedgerStep(
        String from,
        String to,
        double amountBefore,
        double taxRate,
        double taxLoss,
        double amountAfter,
        RuleConfidence confidence) {}
