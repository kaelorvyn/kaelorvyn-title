package com.kael.title.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TitleDataTest {

    @Test
    void chunksStayWithinOldVersionLimits() {
        TitleData data = new TitleData(UUID.randomUUID(), "SteveXiong", "", "小明", "b", "玩家");
        String[] chunks = data.teamChunks();
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 16, "chunk too long: " + chunk);
        }
        assertEquals("\u00A7b[玩家]\u00A7r小明(", chunks[0]);
        assertEquals("SteveXiong", chunks[1]);
        assertEquals(")", chunks[2]);
    }

    @Test
    void longOriginalNameStaysInMiddle() {
        TitleData data = new TitleData(UUID.randomUUID(), "1234567890123456", "", "小明", "b", "玩家");
        String[] chunks = data.teamChunks();
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 16, "chunk too long: " + chunk);
        }
        assertEquals("1234567890123456", chunks[1]);
        assertEquals(")", chunks[2]);
    }

    @Test
    void noNicknameUsesOriginalNameAsMiddle() {
        TitleData data = new TitleData(UUID.randomUUID(), "SteveXiong", "管理员", "", "d", "管理员");
        String[] chunks = data.teamChunks();
        assertEquals("SteveXiong", chunks[1]);
        assertEquals("", chunks[2]);
    }

    @Test
    void combatSuffixIsRedAndAppendedToThirdChunk() {
        TitleData data = new TitleData(UUID.randomUUID(), "SteveXiong", "", "小明", "b", "玩家");
        String suffix = SplitSuffix.forModeName("COMBAT");
        String[] chunks = data.teamChunks(suffix);
        assertEquals(")\u00A7c[\u6218\u6597]", chunks[2]);
        assertEquals("\u00A7b[玩家]\u00A7r小明(SteveXiong)\u00A7c[\u6218\u6597]",
                data.fullDisplay(suffix));
    }

    @Test
    void peacefulSuffixIsGreen() {
        TitleData data = new TitleData(UUID.randomUUID(), "SteveXiong", "", "", "b", "玩家");
        String suffix = SplitSuffix.forModeName("PEACEFUL");
        String[] chunks = data.teamChunks(suffix);
        assertEquals("SteveXiong", chunks[1]);
        assertEquals("\u00A7a[\u548c\u5e73]", chunks[2]);
    }

    @Test
    void neteaseNameUsesOnlyOriginalNameForDisplay() {
        TitleData data = new TitleData(UUID.randomUUID(), "Netease1", "", "旧昵称", "b", "玩家", true,
                "中文玩家");
        assertEquals("\u00A7b[玩家]\u00A7r中文玩家", data.fullDisplay());
        assertEquals("Netease1", data.teamChunks()[1]);
        assertEquals("\u00A7b[玩家]\u00A7r中文玩家（", data.teamChunks()[0]);
        assertEquals("）", data.teamChunks()[2]);
        assertTrue(data.tabDisplay(42).contains("中文玩家（Netease1）"));
    }

    @Test
    void exorcismProfessionReplacesDefaultTitle() {
        TitleData data = new TitleData(UUID.randomUUID(), "SteveXiong", "", "小明", "b", "玩家");
        assertEquals("\u00A7b[法师]\u00A7r小明(SteveXiong)", data.fullDisplay("", "法师"));
        assertEquals("\u00A7b[法师]\u00A7r小明(", data.teamPrefix("法师"));
        assertEquals("\u00A7b[法师]\u00A7r小明(SteveXiong)\u00A7c[\u6218\u6597]",
                data.fullDisplay("\u00A7c[\u6218\u6597]", "法师"));
    }
}
