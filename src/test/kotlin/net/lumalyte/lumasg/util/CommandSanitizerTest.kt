package net.lumalyte.lumasg.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CommandSanitizerTest {

    @Test
    fun `normal name passes through`() {
        assertEquals("Steve", sanitizeCommandArg("Steve"))
    }

    @Test
    fun `Bedrock name with dot and space is collapsed`() {
        assertEquals("BedrockPlayer", sanitizeCommandArg(".Bedrock Player"))
    }

    @Test
    fun `command injection via spaces is collapsed`() {
        assertEquals("absayhi", sanitizeCommandArg("a b say hi"))
    }

    @Test
    fun `special characters are stripped`() {
        assertEquals("Player", sanitizeCommandArg("Player!@#$%"))
    }

    @Test
    fun `underscore is preserved`() {
        assertEquals("Test_Player", sanitizeCommandArg("Test_Player"))
    }

    @Test
    fun `empty result`() {
        assertEquals("", sanitizeCommandArg("!@#"))
    }
}
