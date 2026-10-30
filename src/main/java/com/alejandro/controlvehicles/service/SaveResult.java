package com.alejandro.controlvehicles.service;

import java.util.List;

/** What was saved, and what could not be completed (Google or the price service did not answer). */
public record SaveResult<T>(T saved, List<String> warnings) {
}
