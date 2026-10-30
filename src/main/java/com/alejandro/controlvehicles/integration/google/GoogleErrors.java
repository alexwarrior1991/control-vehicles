package com.alejandro.controlvehicles.integration.google;

import com.alejandro.controlvehicles.integration.ExternalApiException;

import java.util.function.Supplier;

/**
 * What a refused Google call means for whoever set up the key, from the reason in Google's answer: a disabled API,
 * an API missing from the key's restrictions, no billing, an invalid key, an application restriction a desktop
 * application cannot meet, or a quota. A 403 without a reason gets the checklist.
 */
public final class GoogleErrors {

    private GoogleErrors() {
    }

    /** Runs a Google call, turning its failure into one that says what to do. */
    static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (ExternalApiException e) {
            throw explain(e);
        }
    }

    public static ExternalApiException explain(ExternalApiException e) {
        String api = apiName(e.service());
        String reason = e.reason() == null ? "" : e.reason();
        String message = switch (reason) {
            case "SERVICE_DISABLED" -> api + " is not enabled in the Google Cloud project: enable it in APIs & Services"
                    + " → Library (it can take a few minutes to apply).";
            case "API_KEY_SERVICE_BLOCKED" -> "The key's API restrictions do not include " + api + ": add it in"
                    + " Credentials → the key → API restrictions (Places API (New), not the legacy Places API).";
            case "BILLING_DISABLED" -> "Billing is not enabled for the Google Cloud project: link a billing account"
                    + " in Billing.";
            case "API_KEY_INVALID" -> "The key is not valid: copy it again from Credentials → Show key.";
            case "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED", "API_KEY_ANDROID_APP_BLOCKED",
                 "API_KEY_IOS_APP_BLOCKED" -> "The key has an application restriction (websites, IP addresses,"
                    + " Android or iOS) that a desktop application cannot meet: set Application restrictions to"
                    + " None, and keep only the API restrictions.";
            case "RATE_LIMIT_EXCEEDED", "RESOURCE_EXHAUSTED" -> "Google's quota for " + api + " is used up: try"
                    + " later, or raise the quota in Google Cloud.";
            default -> e.status() == 403 ? e.getMessage() + ". Check in Google Cloud that Places API (New) and"
                    + " Routes API are enabled, that both are in the key's API restrictions and that billing is"
                    + " linked; a new or changed key can take a few minutes to work." : null;
        };
        if (message == null) {
            return e;
        }
        return new ExternalApiException(message, e.status(), e.reason(), e.service());
    }

    static String apiName(String service) {
        if (service == null) {
            return "The API";
        }
        return switch (service) {
            case "places.googleapis.com" -> "Places API (New)";
            case "routes.googleapis.com" -> "Routes API";
            default -> service;
        };
    }
}
