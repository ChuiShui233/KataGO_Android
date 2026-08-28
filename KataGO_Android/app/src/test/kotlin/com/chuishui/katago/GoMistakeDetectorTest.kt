package com.chuishui.katago

import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.goai.GoAiEventType
import com.chuishui.katago.goai.GoMistakeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoMistakeDetectorTest {

    private val major = AiGlobalSettings(autoAnalysisMode = AiGlobalSettings.AutoAnalysisMode.MAJOR_MISTAKES)
    private val important = AiGlobalSettings(autoAnalysisMode = AiGlobalSettings.AutoAnalysisMode.IMPORTANT_MISTAKES)

    @Test
    fun offModeNeverEmits() {
        val detector = GoMistakeDetector { AiGlobalSettings(autoAnalysisMode = AiGlobalSettings.AutoAnalysisMode.OFF) }
        assertNull(detector.onMove('B', 1, 0.8f, null))
        assertNull(detector.onMove('B', 2, 0.5f, null))
        assertNull(detector.onMove('B', 3, 0.1f, null))
    }

    @Test
    fun firstMoveHasNoBaseline() {
        val detector = GoMistakeDetector { major }
        assertNull(detector.onMove('B', 1, 0.8f, null))
    }

    @Test
    fun bigDropIsBlunderForBlack() {
        val detector = GoMistakeDetector { major }
        detector.onMove('B', 1, 0.8f, null)
        val event = detector.onMove('B', 2, 0.5f, null, bestCoord = "D4")
        assertEquals(GoAiEventType.BLUNDER, event?.type)
        assertEquals(2, event?.moveNumber)
        assertEquals('B', event?.player)
        assertEquals(30f, event?.deltaPct ?: 0f, 0.01f)
    }

    @Test
    fun smallDropIsMistakeInImportantMode() {
        val detector = GoMistakeDetector { important } // threshold low = 5
        detector.onMove('W', 1, 0.3f, null) // own (white) 0.7
        val event = detector.onMove('W', 2, 0.38f, null) // own 0.62 → loss 8 ≥ 5
        assertEquals(GoAiEventType.MISTAKE, event?.type)
    }

    @Test
    fun whitePerspectiveFlipsWinrate() {
        val detector = GoMistakeDetector { major }
        detector.onMove('W', 1, 0.2f, null) // white 0.8
        val event = detector.onMove('W', 2, 0.5f, null) // white 0.5 → loss 30
        assertEquals(GoAiEventType.BLUNDER, event?.type)
        assertEquals(30f, event?.deltaPct ?: 0f, 0.01f)
        assertEquals(80f, event?.winrateBeforePct ?: 0f, 0.01f)
        assertEquals(50f, event?.winrateAfterPct ?: 0f, 0.01f)
    }

    @Test
    fun gainsAreIgnored() {
        val detector = GoMistakeDetector { major }
        detector.onMove('B', 1, 0.5f, null)
        val event = detector.onMove('B', 2, 0.8f, null) // black improved → no event
        assertNull(event)
    }

    @Test
    fun belowThresholdIsIgnored() {
        val detector = GoMistakeDetector { major } // threshold high = 10
        detector.onMove('B', 1, 0.8f, null)
        val event = detector.onMove('B', 2, 0.77f, null) // loss 3 < 10
        assertNull(event)
    }

    @Test
    fun suspectThresholdBlunderTakesHighThreshold() {
        // IMPORTANT mode: low=5 high=10. A drop of 12 is both >= 5 and >= 10 → BLUNDER.
        val detector = GoMistakeDetector { important }
        detector.onMove('B', 1, 0.8f, null)
        val event = detector.onMove('B', 2, 0.68f, null) // loss 12
        assertEquals(GoAiEventType.BLUNDER, event?.type)
    }

    @Test
    fun missingWinrateReturnsNullButStillViewedAsCompleted() {
        val detector = GoMistakeDetector { major }
        detector.onMove('B', 1, null, null)
        val event = detector.onMove('B', 2, 0.5f, null)
        // Now a real baseline (null → null) exists? before=null → event must be null
        assertNull(event)
    }

    @Test
    fun resetClearsBaseline() {
        val detector = GoMistakeDetector { major }
        detector.onMove('B', 1, 0.9f, null)
        val event = detector.onMove('B', 2, 0.3f, null)
        assertTrue(event?.type == GoAiEventType.BLUNDER)
        detector.reset()
        assertNull(detector.onMove('B', 3, 0.1f, null)) // new baseline, no event
    }
}