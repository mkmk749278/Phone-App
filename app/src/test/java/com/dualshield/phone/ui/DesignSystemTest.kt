package com.dualshield.phone.ui

import com.dualshield.phone.ui.theme.Sizes
import com.dualshield.phone.ui.theme.Spacing
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the design system that are rules rather than taste.
 *
 * Spacing and corner radii are judgement calls and belong in review, not in an assertion.
 * Two things here are not: a control small enough to be hard to hit, and a second copy of a
 * token — because the second copy is what let a 42dp tab past a file that already declared
 * the 48dp minimum three lines above it.
 */
class DesignSystemTest {

    private fun componentSources(): List<File> {
        val root = listOf(
            File("src/main/java/com/dualshield/phone/ui/components"),
            File("app/src/main/java/com/dualshield/phone/ui/components"),
        ).first { it.isDirectory }
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    private fun code(file: File): List<IndexedValue<String>> =
        file.readLines().withIndex().filterNot { (_, line) ->
            val t = line.trimStart()
            t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
        }

    @Test
    fun `the spacing scale is a four dp grid`() {
        // The platform's own components are built on it, so app content and framework
        // content line up only if this does too.
        listOf(Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl)
            .forEach { step ->
                assertEquals(
                    "$step is off the grid",
                    0f,
                    step.value % 4f,
                    0.001f,
                )
            }
    }

    @Test
    fun `the scale ascends`() {
        val steps = listOf(Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl)
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue("$a should be smaller than $b", a.value < b.value)
        }
    }

    @Test
    fun `no interactive control is smaller than a fingertip`() {
        // §99. A modifier chain that both makes something clickable and constrains it below
        // the minimum, in the shared components every screen draws through. A decorative box
        // may be any size it likes.
        //
        // This walks the whole chain rather than one line, which matters: the real defect
        // this caught had `.clickable { ... }` on the line directly above `.heightIn(42.dp)`,
        // and a per-line check reported the file clean.
        val offenders = componentSources().flatMap { file ->
            chainsOf(file).filter { chain ->
                chain.text.contains("clickable") && chain.smallestConstraint() != null
            }.map { "${file.name}:${it.firstLine}: ${it.smallestConstraint()}dp" }
        }
        assertEquals("Controls below the touch target minimum", emptyList<String>(), offenders)
    }

    /** A contiguous `Modifier` chain: the line that starts it plus every `.` line after it. */
    private class Chain(val firstLine: Int, val lines: List<String>) {
        val text: String get() = lines.joinToString("\n")

        /** The smallest height/size constraint in the chain that is under the minimum. */
        fun smallestConstraint(): Int? = Regex("""(height|width|size)(In)?\(\s*(min\s*=\s*)?(\d+)\.dp""")
            .findAll(text)
            .mapNotNull { it.groupValues[4].toIntOrNull() }
            .filter { it < Sizes.minTouchTarget.value }
            .minOrNull()
    }

    private fun chainsOf(file: File): List<Chain> {
        val lines = code(file)
        val chains = mutableListOf<Chain>()
        var i = 0
        while (i < lines.size) {
            val (index, line) = lines[i]
            // A chain starts where a Modifier is introduced and runs while lines keep
            // dotting onto it.
            if (line.contains("Modifier")) {
                val collected = mutableListOf(line)
                var j = i + 1
                while (j < lines.size && lines[j].value.trimStart().startsWith(".")) {
                    collected += lines[j].value
                    j++
                }
                chains += Chain(index + 1, collected)
                i = if (j > i + 1) j else i + 1
            } else {
                i++
            }
        }
        return chains
    }

    @Test
    fun `the touch target minimum is stated once`() {
        // Two declarations of the same number drift, and the one that drifts is never the
        // one being read at the time.
        val declarations = componentSources().flatMap { file ->
            code(file).filter { (_, line) ->
                line.contains(Regex("""val\s+MinTouchTarget\s*=\s*\d+\.dp"""))
            }.map { (i, _) -> "${file.name}:${i + 1}" }
        }
        assertEquals(
            "MinTouchTarget must alias the token, not restate the number",
            emptyList<String>(),
            declarations,
        )
    }

    @Test
    fun `the divider inset follows the row it divides`() {
        // It was a hard-coded 75dp: correct for one row's measurements and quietly wrong
        // after any of them changed.
        assertEquals(
            Spacing.rowPaddingH.value + Sizes.avatarRow.value + Spacing.md.value,
            Sizes.rowDividerInset.value,
            0.001f,
        )
    }

    @Test
    fun `a list row clears the bottom bar`() {
        // The last item of every list sits under the navigation bar and the dialpad button
        // unless something reserves room for them. The oldest call in Recents is where a
        // user notices.
        assertTrue(
            "listBottomInset must clear a navigation bar and a FAB together",
            Spacing.listBottomInset.value >= 88f,
        )
    }
}
