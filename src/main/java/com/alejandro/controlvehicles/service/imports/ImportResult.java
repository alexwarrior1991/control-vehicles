package com.alejandro.controlvehicles.service.imports;

import java.util.List;
import java.util.Set;

/** What an import did: refuels created (and how many with an estimated odometer) and every line left out. */
public record ImportResult(int imported, int estimatedOdometers, int duplicates, int notFuel,
                           Set<String> unknownPlates, List<String> invalid) {
}
