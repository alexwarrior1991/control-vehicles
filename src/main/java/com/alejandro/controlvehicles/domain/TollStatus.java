package com.alejandro.controlvehicles.domain;

/** What Google said about the tolls of a route. */
public enum TollStatus {
    /** The route has no tolls. */
    NONE,
    /** The route has tolls and Google estimated their price. */
    PRICED,
    /** The route has tolls but Google gave no price for them. */
    UNPRICED
}
