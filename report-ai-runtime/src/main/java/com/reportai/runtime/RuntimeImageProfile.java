package com.reportai.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/** Administrator-approved runtime image and capability profile. */
public record RuntimeImageProfile(String language, String imageDigest, String capabilityProfile) {
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9./:_-]+@sha256:[0-9a-fA-F]{64}");
    private static final Pattern CAPABILITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public RuntimeImageProfile {
        if (language == null || language.isBlank() || language.length() > 32) {
            throw new IllegalArgumentException("runtime profile language is invalid");
        }
        language = language.trim().toLowerCase(Locale.ROOT);
        if (imageDigest == null || !IMAGE.matcher(imageDigest.trim()).matches()) {
            throw new IllegalArgumentException("runtime profile image must be digest pinned");
        }
        imageDigest = imageDigest.trim();
        capabilityProfile = capabilityProfile == null || capabilityProfile.isBlank()
                ? "default" : capabilityProfile.trim();
        if (!CAPABILITY.matcher(capabilityProfile).matches()) {
            throw new IllegalArgumentException("runtime profile capability is invalid");
        }
    }

    public RuntimeImageProfile(String language, String imageDigest) {
        this(language, imageDigest, "default");
    }
}
