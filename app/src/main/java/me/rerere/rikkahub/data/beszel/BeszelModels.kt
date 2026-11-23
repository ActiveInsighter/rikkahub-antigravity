package me.rerere.rikkahub.data.beszel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Locale

@Serializable
data class BeszelConfig(
    val serverUrl: String = "",
    val identity: String = "",
    val password: String = "",
    val token: String = "",
    val allowInsecureTls: Boolean = false,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()
}

enum class BeszelChartRange(
    val label: String,
    val type: String,
    val spanMillis: Long,
) {
    HOUR_1("1h", "1m", 1L * 60 * 60 * 1000),
    HOURS_12("12h", "10m", 12L * 60 * 60 * 1000),
    HOURS_24("24h", "20m", 24L * 60 * 60 * 1000),
    WEEK_1("1w", "120m", 7L * 24 * 60 * 60 * 1000),
    MONTH_1("30d", "480m", 30L * 24 * 60 * 60 * 1000),
}

enum class BeszelSystemOs {
    LINUX, DARWIN, WINDOWS, FREEBSD, UNKNOWN;

    val displayName: String
        get() = when (this) {
            LINUX -> "Linux"
            DARWIN -> "macOS"
            WINDOWS -> "Windows"
            FREEBSD -> "FreeBSD"
            UNKNOWN -> "Unknown"
        }

    companion object {
        fun fromValue(value: Int?): BeszelSystemOs = when (value) {
            0 -> LINUX
            1 -> DARWIN
            2 -> WINDOWS
            3 -> FREEBSD
            else -> UNKNOWN
        }
    }
}

@Serializable
data class PocketBaseAuthResponse(
    val token: String = "",
    val record: JsonObject? = null,
)

@Serializable
data class PocketBaseListResponse(
    val page: Int = 1,
    val perPage: Int = 50,
    val totalItems: Int = 0,
    val items: List<JsonObject> = emptyList(),
)

data class BeszelSystem(
    val id: String,
    val name: String,
    val host: String,
    val port: String,
    val status: String,
    val updated: String,
    val info: JsonObject,
) {
    val isUp: Boolean get() = status == "up"
    val isDown: Boolean get() = status == "down"
    val isPaused: Boolean get() = status == "paused"
    val isPending: Boolean get() = status == "pending"

    val cpuPercent: Double get() = info.double("cpu")
    val memoryPercent: Double get() = info.double("mp")
    val diskPercent: Double get() = info.double("dp")

    val bandwidthBytesPerSec: Double
        get() = info.doubleOrNull("bb")
            ?: info.doubleOrNull("b")?.times(1024.0 * 1024.0)
            ?: 0.0

    val uptimeSeconds: Long get() = info.long("u")
    val threads: Int get() = info.int("t")
    val cores: Int get() = info.int("c")
    val cpuModel: String? get() = info.stringOrNull("m")
    val hostname: String? get() = info.stringOrNull("h")
    val kernel: String? get() = info.stringOrNull("k")
    val agentVersion: String? get() = info.stringOrNull("v")?.takeIf { it.isNotBlank() }

    val hasLegacyDetails: Boolean get() = cpuModel != null

    val os: BeszelSystemOs
        get() {
            val osCode = info.intOrNull("os")
            if (osCode != null) return BeszelSystemOs.fromValue(osCode)
            val k = kernel?.lowercase().orEmpty()
            return when {
                "windows" in k -> BeszelSystemOs.WINDOWS
                "darwin" in k -> BeszelSystemOs.DARWIN
                "freebsd" in k -> BeszelSystemOs.FREEBSD
                else -> BeszelSystemOs.LINUX
            }
        }

    val loadAvg: List<Double>
        get() = info.doubleList("la").takeIf { it.size >= 3 && it.any { v -> v != 0.0 } } ?: emptyList()

    val gpuPercent: Double? get() = info.doubleOrNull("g")
    val temperature: Double? get() = info.doubleOrNull("dt")

    val servicesCount: Pair<Int, Int>?
        get() {
            val list = info.intList("sv")
            return if (list.size >= 2) list[0] to list[1] else null
        }

    companion object {
        fun fromJson(obj: JsonObject): BeszelSystem = BeszelSystem(
            id = obj.string("id"),
            name = obj.string("name"),
            host = obj.string("host"),
            port = obj.string("port"),
            status = obj.string("status"),
            updated = obj.string("updated"),
            info = obj["info"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }
}

data class BeszelSystemDetails(
    val hostname: String? = null,
    val kernel: String? = null,
    val cores: Int = 0,
    val threads: Int = 0,
    val cpuModel: String? = null,
    val os: BeszelSystemOs = BeszelSystemOs.UNKNOWN,
    val osName: String? = null,
    val arch: String? = null,
    val memoryTotalBytes: Long? = null,
) {
    companion object {
        fun fromJson(obj: JsonObject): BeszelSystemDetails = BeszelSystemDetails(
            hostname = obj.stringOrNull("hostname"),
            kernel = obj.stringOrNull("kernel"),
            cores = obj.int("cores"),
            threads = obj.int("threads"),
            cpuModel = obj.stringOrNull("cpu"),
            os = BeszelSystemOs.fromValue(obj.intOrNull("os")),
            osName = obj.stringOrNull("os_name"),
            arch = obj.stringOrNull("arch"),
            memoryTotalBytes = obj.longOrNull("memory"),
        )

        fun fromLegacySystem(system: BeszelSystem): BeszelSystemDetails = BeszelSystemDetails(
            hostname = system.hostname,
            kernel = system.kernel,
            cores = system.cores,
            threads = system.threads,
            cpuModel = system.cpuModel,
            os = system.os,
        )
    }
}

data class BeszelStatsPoint(
    val created: String,
    val timestampMillis: Long,
    val stats: JsonObject,
) {
    val cpu: Double get() = stats.double("cpu")
    val loadAvg: List<Double> get() = stats.doubleList("la")

    val memTotalGb: Double get() = stats.double("m")
    val memUsedGb: Double get() = stats.double("mu")
    val memPercent: Double get() = stats.double("mp")
    val memBuffCacheGb: Double get() = stats.double("mb")
    val swapTotalGb: Double get() = stats.double("s")
    val swapUsedGb: Double get() = stats.double("su")

    val diskTotalGb: Double get() = stats.double("d")
    val diskUsedGb: Double get() = stats.double("du")
    val diskPercent: Double get() = stats.double("dp")

    val diskReadBytesPerSec: Double
        get() {
            val dio = stats.doubleList("dio")
            return if (dio.size >= 2) dio[0] else stats.double("dr") * 1024.0 * 1024.0
        }

    val diskWriteBytesPerSec: Double
        get() {
            val dio = stats.doubleList("dio")
            return if (dio.size >= 2) dio[1] else stats.double("dw") * 1024.0 * 1024.0
        }

    val netSentBytesPerSec: Double
        get() {
            val b = stats.doubleList("b")
            return if (b.size >= 2) b[0] else stats.double("ns") * 1024.0 * 1024.0
        }

    val netRecvBytesPerSec: Double
        get() {
            val b = stats.doubleList("b")
            return if (b.size >= 2) b[1] else stats.double("nr") * 1024.0 * 1024.0
        }

    val temperatures: Map<String, Double>
        get() {
            val t = stats["t"] as? JsonObject ?: return emptyMap()
            return t.mapValues { (_, v) -> (v as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
        }

    val extraFs: Map<String, BeszelExtraFs>
        get() {
            val efs = stats["efs"] as? JsonObject ?: return emptyMap()
            return efs.mapNotNull { (k, v) ->
                (v as? JsonObject)?.let { k to BeszelExtraFs(it) }
            }.toMap()
        }

    val gpus: Map<String, BeszelGpu>
        get() {
            val g = stats["g"] as? JsonObject ?: return emptyMap()
            return g.mapNotNull { (k, v) ->
                (v as? JsonObject)?.let { k to BeszelGpu(it) }
            }.toMap()
        }
}

data class BeszelExtraFs(val raw: JsonObject) {
    val totalGb: Double get() = raw.double("d")
    val usedGb: Double get() = raw.double("du")
    val percent: Double get() = if (totalGb > 0) (usedGb / totalGb) * 100.0 else 0.0
}

data class BeszelGpu(val raw: JsonObject) {
    val name: String get() = raw.stringOrNull("n") ?: "GPU"
    val usage: Double get() = raw.double("u")
    val memUsedMb: Double? get() = raw.doubleOrNull("mu")
    val memTotalMb: Double? get() = raw.doubleOrNull("mt")
    val powerWatts: Double? get() = raw.doubleOrNull("p")
}

data class BeszelContainer(
    val id: String,
    val name: String,
    val image: String,
    val ports: String,
    val status: String,
    val health: Int,
    val cpu: Double,
    val memoryMb: Double,
    val netBytesPerSec: Double,
    val updatable: Boolean,
    val updated: Long,
) {
    val isRunning: Boolean get() = status.lowercase().startsWith("up")

    companion object {
        fun fromJson(obj: JsonObject): BeszelContainer = BeszelContainer(
            id = obj.string("id"),
            name = obj.string("name"),
            image = obj.string("image"),
            ports = obj.string("ports"),
            status = obj.string("status"),
            health = obj.int("health"),
            cpu = obj.double("cpu"),
            memoryMb = obj.double("memory"),
            netBytesPerSec = obj.double("net"),
            updatable = (obj["updatable"] as? JsonPrimitive)?.booleanOrNull == true,
            updated = obj.long("updated"),
        )
    }
}

data class BeszelSystemdService(
    val id: String,
    val name: String,
    val state: Int,
    val sub: Int,
    val cpu: Double,
    val memoryBytes: Double,
    val updated: Long,
) {
    val stateName: String
        get() = listOf("active", "inactive", "failed", "activating", "deactivating", "reloading")
            .getOrElse(state) { "unknown" }

    val subName: String
        get() = listOf("dead", "running", "exited", "failed", "unknown")
            .getOrElse(sub) { "unknown" }

    val isFailed: Boolean get() = state == 2 || sub == 3
    val isActive: Boolean get() = state == 0

    companion object {
        fun fromJson(obj: JsonObject): BeszelSystemdService = BeszelSystemdService(
            id = obj.string("id"),
            name = obj.string("name"),
            state = obj.int("state"),
            sub = obj.int("sub"),
            cpu = obj.double("cpu"),
            memoryBytes = obj.double("memory"),
            updated = obj.long("updated"),
        )
    }
}

data class BeszelAlertEvent(
    val id: String,
    val name: String,
    val monitorName: String,
    val value: Double,
    val systemId: String,
    val systemName: String,
    val created: String,
    val resolved: String,
) {
    val isResolved: Boolean get() = resolved.isNotBlank()

    companion object {
        fun fromJson(obj: JsonObject): BeszelAlertEvent {
            val expand = obj["expand"] as? JsonObject
            val expandedSystem = expand?.get("system") as? JsonObject
            return BeszelAlertEvent(
                id = obj.string("id"),
                name = obj.string("name"),
                monitorName = obj.string("monitor_name"),
                value = obj.double("value"),
                systemId = obj.string("system"),
                systemName = expandedSystem?.stringOrNull("name") ?: obj.string("system"),
                created = obj.string("created"),
                resolved = obj.string("resolved"),
            )
        }
    }
}

object BeszelFormatter {
    fun formatBytesPerSec(bytesPerSec: Double): String {
        if (bytesPerSec <= 0.0) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")
        var value = bytesPerSec
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.lastIndex) {
            value /= 1024.0
            unitIdx++
        }
        return if (unitIdx == 0) {
            "${value.toInt()} ${units[unitIdx]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIdx])
        }
    }

    fun formatBytes(bytes: Double): String {
        if (bytes <= 0.0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.lastIndex) {
            value /= 1024.0
            unitIdx++
        }
        return if (unitIdx == 0) {
            "${value.toInt()} ${units[unitIdx]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIdx])
        }
    }

    fun formatGigabytes(gb: Double): String {
        if (gb <= 0.0) return "0 GB"
        return if (gb >= 1024.0) {
            String.format(Locale.US, "%.2f TB", gb / 1024.0)
        } else if (gb < 1.0) {
            String.format(Locale.US, "%.0f MB", gb * 1024.0)
        } else {
            String.format(Locale.US, "%.1f GB", gb)
        }
    }

    fun formatUptime(seconds: Long): String {
        if (seconds <= 0L) return "-"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}天 ${hours}小时"
            hours > 0 -> "${hours}小时 ${mins}分"
            else -> "${mins}分钟"
        }
    }

    fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value.coerceIn(0.0, 100.0))
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.content ?: ""

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.double(key: String): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

private fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

private fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.doubleList(key: String): List<Double> {
    val arr = this[key] ?: return emptyList()
    return runCatching {
        arr.jsonArray.mapNotNull { it.jsonPrimitive.doubleOrNull }
    }.getOrDefault(emptyList())
}

private fun JsonObject.intList(key: String): List<Int> {
    val arr = this[key] ?: return emptyList()
    return runCatching {
        arr.jsonArray.mapNotNull { it.jsonPrimitive.intOrNull }
    }.getOrDefault(emptyList())
}
