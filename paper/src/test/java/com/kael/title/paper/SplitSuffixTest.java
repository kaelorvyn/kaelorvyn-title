package com.kael.title.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class SplitSuffixTest {

    @Test
    void mapsModesToRequestedColors() {
        assertEquals("\u00A7c[\u6218\u6597]", SplitSuffix.forModeName("COMBAT"));
        assertEquals("\u00A7a[\u548c\u5e73]", SplitSuffix.forModeName("PEACEFUL"));
        assertEquals("", SplitSuffix.forModeName("UNKNOWN"));
    }

    @Test
    void legacyProfileUsesWhiteNoSplitSuffix() {
        assertEquals("\u00A7f[\u65e0\u5206\u6d41]", SplitSuffix.forProfile("COMBAT", true));
        assertEquals("\u00A7f[\u65e0\u5206\u6d41]", SplitSuffix.forProfile("PEACEFUL", true));
        assertEquals("\u00A7f[\u65e0\u5206\u6d41]", SplitSuffix.forModeName("NONE"));
    }
}
