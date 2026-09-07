package io.celox.flipperripper.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Guards the app's drawn marks — the launcher icon and the three in-app signs — as *files*, because
 * that is where they can silently rot: a lost `evenOdd`, a glyph drifting outside the adaptive-icon
 * safe zone, or a monochrome layer that quietly points back at the coloured foreground.
 *
 * The geometry is measured with [PathBounds], a real (if small) path parser. A regex over the path
 * string is NOT good enough and this test exists partly because of that: reading `a3.00,3.00 0 0 1
 * 0,6.00` or `l-13.00,12.50` as absolute coordinates reported the glyph 48dp outside the canvas when
 * it was comfortably inside.
 */
class VectorDrawableTest {
    private val drawables = File("src/main/res/drawable")

    private fun xml(name: String): String {
        val f = File(drawables, "$name.xml")
        assertThat(f.exists()).isTrue()
        return f.readText()
    }

    private fun pathData(xml: String): List<String> =
        Regex("""android:pathData="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()

    private val marks = listOf("ic_launcher_foreground", "ic_launcher_monochrome", "ic_app_mark", "ic_video_placeholder")

    @Test
    fun `every mark is drawn on the 108dp adaptive-icon canvas`() {
        (marks + "ic_downloads_empty").forEach { name ->
            val x = xml(name)
            assertThat(x).contains("android:viewportWidth=\"108\"")
            assertThat(x).contains("android:viewportHeight=\"108\"")
        }
    }

    @Test
    fun `the glyph is a hole, not an overlay`() {
        // Two things make a hole: evenOdd, and more than one subpath in the same path. Lose either and
        // the arrow stops being cut out — it becomes a shape sitting on top, which breaks the moment
        // the mark is drawn on a coloured or tinted surface.
        marks.forEach { name ->
            val x = xml(name)
            assertThat(x).contains("android:fillType=\"evenOdd\"")
            val subpaths = pathData(x).first().count { it == 'M' }
            assertThat(subpaths).isAtLeast(2)
        }
    }

    @Test
    fun `the mark stays inside the adaptive-icon safe zone`() {
        // 108dp canvas, 66dp safe zone -> everything must live within 33dp of the centre.
        marks.forEach { name ->
            val bounds = PathBounds.of(pathData(xml(name)).joinToString(" "))
            assertThat(bounds.maxRadiusFrom(54.0, 54.0)).isLessThan(SAFE_ZONE_RADIUS)
        }
    }

    @Test
    fun `the punched glyph sits well inside the disc, not across its edge`() {
        // The disc's narrowest point is its lobe valleys; a glyph reaching past that cuts the outline
        // open and reads as a stray stem. Measured against the disc: glyph must stay clear of it.
        val paths = pathData(xml("ic_app_mark")).first()
        val subpaths = paths.split(Regex("(?=M)")).filter { it.isNotBlank() }
        val disc = PathBounds.of(subpaths.first())
        val glyph = PathBounds.of(subpaths.drop(1).joinToString(" "))
        assertThat(glyph.maxRadiusFrom(54.0, 54.0)).isLessThan(disc.minRadiusFrom(54.0, 54.0))
    }

    @Test
    fun `the themed-icon layer is its own file and one flat colour`() {
        val adaptive = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertThat(adaptive).contains("@drawable/ic_launcher_monochrome")
        // Pointing <monochrome> at the coloured foreground ships that tint into a layer the system is
        // supposed to colour itself.
        assertThat(adaptive).doesNotContain("<monochrome android:drawable=\"@drawable/ic_launcher_foreground\"")
        val mono = xml("ic_launcher_monochrome")
        assertThat(Regex("""android:fillColor="([^"]+)"""").findAll(mono).map { it.groupValues[1] }.toSet())
            .containsExactly("#FF000000")
        assertThat(mono).doesNotContain("gradient")
    }

    @Test
    fun `in-app marks are black so the caller's tint decides the colour`() {
        listOf("ic_app_mark", "ic_downloads_empty", "ic_video_placeholder").forEach { name ->
            Regex("""android:fillColor="([^"]+)"""").findAll(xml(name)).forEach {
                assertThat(it.groupValues[1]).isEqualTo("#FF000000")
            }
        }
    }

    @Test
    fun `the parser reads relative commands and arcs, not just absolute pairs`() {
        // Hand-computed. This is the parser the safe-zone check leans on, and the exact thing an
        // earlier regex-based attempt got wrong: it read the deltas of `h`/`l` and the radii of `a`
        // as absolute coordinates and reported a glyph 48dp outside a canvas it comfortably fits.
        // Square from (10,10) to (20,20) via relative moves.
        val relative = PathBounds.of("M10,10 h10 v10 h-10 Z")
        assertThat(relative.maxRadiusFrom(0.0, 0.0)).isWithin(0.01).of(hypot(20.0, 20.0))
        assertThat(relative.minRadiusFrom(0.0, 0.0)).isWithin(0.01).of(hypot(10.0, 10.0))

        // An arc: only "0,6" is a point — the two radii and three flags before it are not.
        // From (50,50) the pen ends at (50,56); nothing may be read at radius 3 or 1.
        val arc = PathBounds.of("M50,50 a3,3 0 0 1 0,6")
        assertThat(arc.maxRadiusFrom(50.0, 50.0)).isWithin(0.01).of(6.0)
        assertThat(arc.minRadiusFrom(50.0, 50.0)).isWithin(0.01).of(0.0)

        // Z returns to the subpath start, so a following relative command starts from there.
        val closed = PathBounds.of("M10,10 h10 Z h5")
        assertThat(closed.maxRadiusFrom(10.0, 10.0)).isWithin(0.01).of(10.0)
    }

    private companion object {
        const val SAFE_ZONE_RADIUS = 33.0
    }
}

/**
 * The extreme points of an SVG/VectorDrawable path. Deliberately handles the commands these marks
 * actually use — M/L/H/V/C/A and their relative forms — because the parameters of `a` and the deltas
 * of the relative commands are exactly what a naive coordinate scan misreads as absolute points.
 *
 * Curve control points are included: they bound the curve, so a shape that fits by control points
 * certainly fits by its outline.
 */
internal class PathBounds private constructor(private val points: List<Pair<Double, Double>>) {
    fun maxRadiusFrom(cx: Double, cy: Double): Double = points.maxOf { hypot(it.first - cx, it.second - cy) }

    fun minRadiusFrom(cx: Double, cy: Double): Double = points.minOf { hypot(it.first - cx, it.second - cy) }

    /** Walks a path command by command, keeping the current point and collecting every coordinate. */
    private class Cursor {
        val points = mutableListOf<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        private var startX = 0.0
        private var startY = 0.0

        fun apply(cmd: Char, args: List<Double>) {
            val upper = cmd.uppercaseChar()
            val arity = ARITY[upper] ?: return
            if (upper == 'Z') {
                x = startX
                y = startY
                return
            }
            args.chunked(arity).filter { it.size == arity }.forEach { chunk -> step(upper, cmd.isLowerCase(), chunk) }
        }

        private fun step(upper: Char, relative: Boolean, a: List<Double>) {
            when (upper) {
                'H' -> x = if (relative) x + a[0] else a[0]
                'V' -> y = if (relative) y + a[0] else a[0]
                // An arc's radii and flags are not coordinates — only its final pair is.
                'A' -> moveTo(relative, a[5], a[6])
                else -> {
                    // Every (x, y) pair bounds the curve, so control points count as extremes.
                    a.chunked(2).forEach { p -> points += resolve(relative, p[0], p[1]) }
                    moveTo(relative, a[a.size - 2], a[a.size - 1])
                }
            }
            points += x to y
            if (upper == 'M') {
                startX = x
                startY = y
            }
        }

        private fun resolve(relative: Boolean, dx: Double, dy: Double): Pair<Double, Double> =
            if (relative) (x + dx) to (y + dy) else dx to dy

        private fun moveTo(relative: Boolean, dx: Double, dy: Double) {
            val (nx, ny) = resolve(relative, dx, dy)
            x = nx
            y = ny
        }
    }

    companion object {
        /** Parameters per command; `A` takes 7 but only its last two are a coordinate. */
        private val ARITY = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'A' to 7, 'Z' to 0)
        private val TOKEN = Regex("""([MLHVCSQAZmlhvcsqaz])|(-?\d*\.?\d+(?:e-?\d+)?)""")

        fun of(path: String): PathBounds {
            val cursor = Cursor()
            var cmd = 'M'
            val args = mutableListOf<Double>()
            TOKEN.findAll(path).forEach { m ->
                val c = m.groupValues[1]
                if (c.isEmpty()) {
                    args += m.groupValues[2].toDouble()
                } else {
                    cursor.apply(cmd, args)
                    args.clear()
                    cmd = c[0]
                }
            }
            cursor.apply(cmd, args)
            require(cursor.points.isNotEmpty()) { "no points parsed from path" }
            return PathBounds(cursor.points)
        }
    }
}
