package com.fireworm.colourfun.generator.random

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcg32Test {

    @Test
    fun `matches the reference implementation`() {
        // pcg32-demo: pcg32_srandom(42, 54), first six outputs.
        val random = Pcg32(seed = 42, stream = 54)
        val expected = listOf(0xa15c02b7, 0x7b47f409, 0xba1d3330, 0x83d2f293, 0xbfa4784b, 0xcbed606e)

        expected.forEach { assertEquals(it.toInt(), random.nextInt()) }
    }

    @Test
    fun `the same seed gives the same sequence and another seed a different one`() {
        val a = List(100) { Pcg32(7).let { r -> List(10) { r.nextInt() } } }.distinct()
        assertEquals(1, a.size)

        val first = Pcg32(7).nextInt()
        assertTrue(Pcg32(8).nextInt() != first)
    }

    @Test
    fun `floats are in range and gaussians have mean 0 and deviation 1`() {
        val random = Pcg32(1)
        repeat(10_000) { assertTrue(random.nextFloat() in 0f..<1f) }

        val samples = List(20_000) { random.nextGaussian() }
        val mean = samples.average()
        val deviation = kotlin.math.sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        assertTrue("mean $mean", abs(mean) < 0.03)
        assertTrue("deviation $deviation", abs(deviation - 1) < 0.03)
    }
}
