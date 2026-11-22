package me.rerere.rikkahub.ui.components.richtext

import coil3.annotation.ExperimentalCoilApi
import coil3.svg.Svg
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException

private val svg2Orientation = "auto-start-reverse".encodeUtf8()

@OptIn(ExperimentalCoilApi::class)
internal val compatibleSvgParser = Svg.Parser { source ->
    if (source.request(16L * 1024 * 1024 + 1)) throw IOException("SVG exceeds the preview size limit")
    if (source.buffer.indexOf(svg2Orientation) < 0) {
        Svg.Parser.DEFAULT.parse(source)
    } else {
        Svg.Parser.DEFAULT.parse(Buffer().writeUtf8(compatibleSvgMarkers(source.readUtf8())))
    }
}

private val markerElement = Regex(
    """<marker\b((?:[^>"'/]|/(?!>)|"[^"]*"|'[^']*')*+)(?:/>|>(.*?)</marker\s*>)""",
    RegexOption.DOT_MATCHES_ALL,
)
private val markerStart = Regex("""(\bmarker-start\s*(?:=\s*["']\s*|:\s*)url\(\s*(?:&quot;|["'])?#)([^\s"')]+)(\s*(?:&quot;|["'])?\s*\))""")
private val markerAttribute = Regex("""\s+([\w:-]+)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
private val viewportSeparator = Regex("""[\s,]+""")

/** AndroidSVG supports SVG 1.1's auto orientation. Generate a flipped copy for start references. */
internal fun compatibleSvgMarkers(svg: String): String {
    val startIds = markerStart.findAll(svg).map { it.groupValues[2] }.toSet()
    val flippedIds = mutableMapOf<String, String>()
    val markerDefinitions = svg.replace(markerElement) { marker ->
        val originalAttributes = marker.groupValues[1]
        val attributes = markerAttribute.findAll(originalAttributes).associateBy { it.groupValues[1] }
        val orientation = attributes["orient"]
        if (orientation?.groupValues?.get(3) != "auto-start-reverse") return@replace marker.value

        val autoAttributes = originalAttributes.replaceRange(orientation.range, " orient=\"auto\"")
        val contents = marker.groups[2]?.value ?: return@replace "<marker$autoAttributes/>"
        val base = "<marker$autoAttributes>$contents</marker>"
        val markerId = attributes["id"]?.groupValues?.get(3) ?: return@replace base
        if (markerId !in startIds) return@replace base
        val viewBox = attributes["viewBox"]?.groupValues?.get(3)?.trim()
            ?.split(viewportSeparator)?.mapNotNull(String::toFloatOrNull)
        val centerX = if (viewBox?.size == 4) viewBox[0] + viewBox[2] / 2 else markerLength(attributes["markerWidth"], 3f) / 2
        val centerY = if (viewBox?.size == 4) viewBox[1] + viewBox[3] / 2 else markerLength(attributes["markerHeight"], 3f) / 2
        val refX = 2 * centerX - markerLength(attributes["refX"], 0f)
        val refY = 2 * centerY - markerLength(attributes["refY"], 0f)

        var serial = flippedIds.size
        var flippedId: String
        do {
            flippedId = "rikka-svg-reversed-${serial++}"
        } while (flippedId in svg || flippedId in flippedIds.values)
        flippedIds[markerId] = flippedId
        val flippedAttributes = markerAttribute.replace(autoAttributes) { attr ->
            if (attr.groupValues[1] in setOf("id", "refX", "refY")) "" else attr.value
        }

        base + """<marker id="$flippedId" refX="$refX" refY="$refY"$flippedAttributes>""" +
            """<g transform="rotate(180 $centerX $centerY)">$contents</g></marker>"""
    }
    return markerStart.replace(markerDefinitions) { reference ->
        val replacement = flippedIds[reference.groupValues[2]] ?: return@replace reference.value
        reference.groupValues[1] + replacement + reference.groupValues[3]
    }
}

private fun markerLength(attribute: MatchResult?, fallback: Float): Float =
    attribute?.groupValues?.get(3)?.removeSuffix("px")?.toFloatOrNull()?.takeIf(Float::isFinite) ?: fallback
