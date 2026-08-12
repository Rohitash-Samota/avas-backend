package com.avas.platform.auth;

public record ProviderStatus(boolean local, boolean google, String googleAuthorizationUrl) {
}
