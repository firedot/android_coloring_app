package com.fireworm.colourfun.ui.gate

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentGateChallengeTest {

    private val words = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    @Test
    fun `digits are written as words and answered as digits`() {
        val challenge = ParentGateChallenge(listOf(7, 0, 9, 4))

        assertEquals("seven, zero, nine, four", challenge.words(words))
        assertTrue(challenge.isAnswer("7094"))
        assertTrue(challenge.isAnswer(" 7094 "))
        assertFalse(challenge.isAnswer("794"))
        assertFalse(challenge.isAnswer("seven zero nine four"))
        assertFalse(challenge.isAnswer(""))
    }

    @Test
    fun `random challenges have four digits and vary`() {
        val random = Random(1)
        val challenges = List(50) { ParentGateChallenge.random(random).digits }

        assertTrue(challenges.all { it.size == ParentGateChallenge.LENGTH && it.all { d -> d in 0..9 } })
        assertTrue(challenges.toSet().size > 40)
    }
}
