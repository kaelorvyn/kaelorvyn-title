package com.kael.title.paper;

public final class SplitSuffix {

    public static String forModeName(String modeName) {
        return forProfile(modeName, false);
    }

    public static String forProfile(String modeName, boolean legacy) {
        if (legacy || "NONE".equals(modeName) || "UNSPLIT".equals(modeName)) {
            return "\u00A7f[\u65e0\u5206\u6d41]";
        }
        if ("COMBAT".equals(modeName)) {
            return "\u00A7c[\u6218\u6597]";
        }
        if ("PEACEFUL".equals(modeName)) {
            return "\u00A7a[\u548c\u5e73]";
        }
        return "";
    }

    private SplitSuffix() {
    }
}
