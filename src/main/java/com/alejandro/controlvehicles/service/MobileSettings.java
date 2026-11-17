package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Texts;

/**
 * The phone app's addresses: where it is published ({@code appUrl}, GitHub Pages), the Apps Script web app it and
 * the desktop talk to ({@code apiUrl}) and that script's admin secret.
 */
public record MobileSettings(String appUrl, String apiUrl, String secret) {

    public MobileSettings {
        appUrl = Texts.trimToNull(appUrl) == null ? SettingsService.DEFAULT_MOBILE_APP_URL : appUrl.trim();
        apiUrl = apiUrl == null ? "" : apiUrl.trim();
        secret = secret == null ? "" : secret.trim();
    }

    public boolean configured() {
        return !apiUrl.isEmpty() && !secret.isEmpty();
    }
}
