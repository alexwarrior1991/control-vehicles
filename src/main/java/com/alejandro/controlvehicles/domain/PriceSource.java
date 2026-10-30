package com.alejandro.controlvehicles.domain;

/** Where a stored fuel price came from. */
public enum PriceSource {
    /** Spanish Ministry (MITECO) station prices. */
    MITECO,
    /** Portuguese DGEG station prices. */
    DGEG,
    /** Typed by hand. */
    MANUAL
}
