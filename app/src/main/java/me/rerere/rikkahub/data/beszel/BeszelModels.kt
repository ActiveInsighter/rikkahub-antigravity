package me.rerere.rikkahub.data.beszel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class BeszelSession(
    val baseUrl: String,
    val identity: String,
    val token: String,
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
)

@Serializable
data class BeszelSavedCredentials(
    val baseUrl: String = "",
    val identity: String = "",
)

enum class BeszelSystemOs {
    LINUX,
    DARWIN,
    WINDOWS,
    FREEBSD,
    UNKNOWN;

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

enum class BeszelBatteryState {
    UNKNOWN,
    EMPTY,
    FULL,
    CHARGING,
    DISCHARGING,
    IDLE;

    companion object {
        fun fromValue(value: Int?): BeszelBatteryState =
            entries.getOrNull(value ?: -1) ?: UNKNOWN
    }
}

enum class BeszelChartRange(
    val label: String,
    val type: String,
    val spanMinutes: Long,
) {
    LIVE("实时", "1m", 1L),
    HOUR("1h", "1m", 60L),
    HALF_DAY("12h", "10m", 12 * 60L),
    DAY("24h", "20m", 24 * 60L),
    WEEK("7d", "120m", 7 * 24 * 60L),
    MONTH("30d", "480m", 30 * 24 * 60L),
}

data class BeszelSystem(
    val id: String,
    val name: String,
    val host: String,
    val port: String,
    val status: String, // up, down, paused, pending
    val updated: String,
    val info: JsonObject,
) {
    val isUp: Boolean get() = status == "up"
    val isDown: Boolean get() = status == "down"
    val isPaused: Boolean get() = status == "paused"
    val isPending: Boolean get() = status == "pending"

    val cpuPercent: Double get() = info.doubleValue("cpu")
    val memoryPercent: Double get() = info.doubleValue("mp")
    val diskPercent: Double get() = info.doubleValue("dp")

    /** Combined network bandwidth in bytes/s (`bb`), falling back to deprecated MB/s (`b`). */
    val bandwidthBytesPerSec: Double
        get() = info.doubleOrNull("bb")
            ?: info.doubleOrNull("b")?.times(1024.0 * 1024.0)
            ?: 0.0

    val uptimeSeconds: Long get() = info.longValue("u")
    val threads: Int get() = info.intValue("t")
    val cores: Int get() = info.intValue("c")
    val cpuModel: String? get() = info.stringOrNull("m")
    val hostname: String? get() = info.stringOrNull("h")
    val kernel: String? get() = info.stringOrNull("k")
    val agentVersion: String? get() = info.stringOrNull("v")?.takeIf { it.isNotBlank() }

    val hasNoData: Boolean
        get() = agentVersion == null && uptimeSeconds == 0L && cpuPercent == 0.0

    val hasLegacyDetails: Boolean
        get() = info.stringOrNull("m") != null

    val os: BeszelSystemOs
        get() {
            val osVal = info.intOrNull("os")
            if (osVal != null) return BeszelSystemOs.fromValue(osVal)
            val k = kernel?.lowercase().orEmpty()
            return when {
                "windows" in k -> BeszelSystemOs.WINDOWS
                "darwin" in k -> BeszelSystemOs.DARWIN
                "freebsd" in k -> BeszelSystemOs.FREEBSD
                else -> BeszelSystemOs.LINUX
            }
        }

    val loadAvg: List<Double>
        get() {
            val la = info.doubleList("la")
            return if (la.size >= 3 && la.any { it != 0.0 }) la else emptyList()
        }

    val gpuPercent: Double? get() = info.doubleOrNull("g")
    val temperature: Double? get() = info.doubleOrNull("dt")

    val extraFs: Map<String, Double>
        get() = info.objectOrNull("efs")
            ?.mapValues { (_, v) -> v.asDoubleValue() }
            ?: emptyMap()

    val rootDiskName: String? get() = info.stringOrNull("rdn")

    /** systemd services [total, failed] */
    val servicesSummary: Pair<Int, Int>?
        get() {
            val arr = info.arrayOrNull("sv") ?: return null
            if (arr.size < 2) return null
            return arr[0].asIntValue() to arr[1].asIntValue()
        }

    /** Battery [percent, state] */
    val battery: Pair<Int, BeszelBatteryState>?
        get() {
            val arr = info.arrayOrNull("bat") ?: return null
            if (arr.size < 2) return null
            return arr[0].asIntValue() to BeszelBatteryState.fromValue(arr[1].asIntValue())
        }

    /** Pending package updates [total, security?] */
    val pendingUpdates: List<Int>
        get() = info.arrayOrNull("pu")?.map { it.asIntValue() } ?: emptyList()

    /** Connection type: 1 SSH, 2 WebSocket */
    val connectionType: Int get() = info.intValue("ct")
}

data class BeszelSystemDetails(
    val hostname: String,
    val kernel: String,
    val cpu: String,
    val osName: String,
    val arch: String,
    val os: BeszelSystemOs,
    val cores: Int,
    val threads: Int,
    val memoryBytes: Double,
    val podman: Boolean,
) {
    companion object {
        fun fromRecord(obj: JsonObject): BeszelSystemDetails = BeszelSystemDetails(
            hostname = obj.stringOrNull("hostname").orEmpty(),
            kernel = obj.stringOrNull("kernel").orEmpty(),
            cpu = obj.stringOrNull("cpu").orEmpty(),
            osName = obj.stringOrNull("os_name").orEmpty(),
            arch = obj.stringOrNull("arch").orEmpty(),
            os = BeszelSystemOs.fromValue(obj.intOrNull("os")),
            cores = obj.intValue("cores"),
            threads = obj.intValue("threads"),
            memoryBytes = obj.doubleValue("memory"),
            podman = obj.booleanValue("podman"),
        )

        fun fromLegacyInfo(s: BeszelSystem): BeszelSystemDetails = BeszelSystemDetails(
            hostname = s.hostname.orEmpty(),
            kernel = s.kernel.orEmpty(),
            cpu = s.cpuModel.orEmpty(),
            osName = "",
            arch = "",
            os = s.os,
            cores = s.cores,
            threads = s.threads,
            memoryBytes = 0.0,
            podman = s.info.booleanValue("p"),
        )
    }
}

data class BeszelExtraFsStat(
    val name: String,
    val totalGb: Double,
    val usedGb: Double,
    val readBytesPerSec: Double,
    val writeBytesPerSec: Double,
) {
    val percent: Double get() = if (totalGb > 0) usedGb / totalGb * 100.0 else 0.0
}

data class BeszelGpuStat(
    val id: String,
    val name: String,
    val usage: Double,
    val memUsedMb: Double?,
    val memTotalMb: Double?,
    val powerWatts: Double?,
)

data class BeszelStoragePoolStat(
    val id: String,
    val name: String,
    val totalGib: Double,
    val usedGib: Double,
    val health: String?,
    val hideUsage: Boolean,
) {
    val percent: Double get() = if (totalGib > 0) usedGib / totalGib * 100.0 else 0.0
}

data class BeszelStatsPoint(
    val time: Instant,
    val stats: JsonObject,
) {
    val cpu: Double get() = stats.doubleValue("cpu")
    val cpuMax: Double? get() = stats.doubleOrNull("cpum")
    val cpuCores: List<Double> get() = stats.doubleList("cpus")
    val loadAvg: List<Double> get() = stats.doubleList("la")

    val memTotalGb: Double get() = stats.doubleValue("m")
    val memUsedGb: Double get() = stats.doubleValue("mu")
    val memPercent: Double get() = stats.doubleValue("mp")
    val memBuffCacheGb: Double get() = stats.doubleValue("mb")
    val memZfsArcGb: Double get() = stats.doubleValue("mz")
    val swapTotalGb: Double get() = stats.doubleValue("s")
    val swapUsedGb: Double get() = stats.doubleValue("su")

    val diskTotalGb: Double get() = stats.doubleValue("d")
    val diskUsedGb: Double get() = stats.doubleValue("du")
    val diskPercent: Double get() = stats.doubleValue("dp")

    val diskReadBytesPerSec: Double
        get() {
            val dio = stats.doubleList("dio")
            return if (dio.size >= 2) dio[0] else stats.doubleValue("dr") * 1024.0 * 1024.0
        }

    val diskWriteBytesPerSec: Double
        get() {
            val dio = stats.doubleList("dio")
            return if (dio.size >= 2) dio[1] else stats.doubleValue("dw") * 1024.0 * 1024.0
        }

    val netSentBytesPerSec: Double
        get() {
            val b = stats.doubleList("b")
            return if (b.size >= 2) b[0] else stats.doubleValue("ns") * 1024.0 * 1024.0
        }

    val netRecvBytesPerSec: Double
        get() {
            val b = stats.doubleList("b")
            return if (b.size >= 2) b[1] else stats.doubleValue("nr") * 1024.0 * 1024.0
        }

    val temperatures: Map<String, Double>
        get() = stats.objectOrNull("t")
            ?.mapValues { (_, v) -> v.asDoubleValue() }
            ?: emptyMap()

    val fans: Map<String, Double>
        get() = stats.objectOrNull("f")
            ?.mapValues { (_, v) -> v.asDoubleValue() }
            ?: emptyMap()

    val extraFs: List<BeszelExtraFsStat>
        get() = stats.objectOrNull("efs")?.map { (name, v) ->
            val raw = (v as? JsonObject) ?: JsonObject(emptyMap())
            BeszelExtraFsStat(
                name = name,
                totalGb = raw.doubleValue("d"),
                usedGb = raw.doubleValue("du"),
                readBytesPerSec = raw.doubleOrNull("rb") ?: (raw.doubleValue("r") * 1048576.0),
                writeBytesPerSec = raw.doubleOrNull("wb") ?: (raw.doubleValue("w") * 1048576.0),
            )
        } ?: emptyList()

    val gpus: List<BeszelGpuStat>
        get() = stats.objectOrNull("g")?.map { (id, v) ->
            val raw = (v as? JsonObject) ?: JsonObject(emptyMap())
            BeszelGpuStat(
                id = id,
                name = raw.stringOrNull("n") ?: "GPU",
                usage = raw.doubleValue("u"),
                memUsedMb = raw.doubleOrNull("mu"),
                memTotalMb = raw.doubleOrNull("mt"),
                powerWatts = raw.doubleOrNull("p"),
            )
        } ?: emptyList()

    val pools: List<BeszelStoragePoolStat>
        get() = stats.objectOrNull("z")?.map { (id, v) ->
            val raw = (v as? JsonObject) ?: JsonObject(emptyMap())
            BeszelStoragePoolStat(
                id = id,
                name = raw.stringOrNull("n") ?: id,
                totalGib = raw.doubleValue("d"),
                usedGib = raw.doubleValue("du"),
                health = raw.stringOrNull("h"),
                hideUsage = raw.booleanValue("hu"),
            )
        } ?: emptyList()

    val batteries: Map<String, Double>
        get() = stats.objectOrNull("bats")
            ?.mapValues { (_, v) -> v.asDoubleValue() }
            ?: emptyMap()
}

data class BeszelContainer(
    val id: String,
    val name: String,
    val image: String,
    val ports: String,
    val status: String,
    val health: Int, // 0 none, 1 starting, 2 healthy, 3 unhealthy
    val cpu: Double,
    val memoryMb: Double,
    val netBytesPerSec: Double,
    val updatable: Boolean,
    val updated: Long,
) {
    val isRunning: Boolean get() = status.lowercase().startsWith("up")

    val healthLabel: String?
        get() = when (health) {
            1 -> "启动中"
            2 -> "健康"
            3 -> "异常"
            else -> null
        }
}

data class BeszelSystemdService(
    val id: String,
    val name: String,
    val state: Int, // 0 active, 1 inactive, 2 failed, 3 activating, 4 deactivating, 5 reloading
    val sub: Int, // 0 dead, 1 running, 2 exited, 3 failed, 4 unknown
    val cpu: Double,
    val cpuPeak: Double,
    val memoryBytes: Double,
    val memPeakBytes: Double,
    val updated: Long,
) {
    val stateName: String get() = STATE_NAMES.getOrElse(state) { "unknown" }
    val subName: String get() = SUB_NAMES.getOrElse(sub) { "unknown" }
    val isFailed: Boolean get() = state == 2 || sub == 3
    val isActive: Boolean get() = state == 0

    companion object {
        private val STATE_NAMES = listOf("active", "inactive", "failed", "activating", "deactivating", "reloading")
        private val SUB_NAMES = listOf("dead", "running", "exited", "failed", "unknown")
    }
}

data class BeszelSmartDevice(
    val id: String,
    val name: String,
    val model: String,
    val serial: String,
    val firmware: String,
    val state: String,
    val type: String,
    val capacityBytes: Double,
    val tempCelsius: Double?,
    val hours: Int,
    val cycles: Int,
    val updated: String,
) {
    val passed: Boolean get() = state.equals("PASSED", ignoreCase = true)
    val failed: Boolean get() = state.equals("FAILED", ignoreCase = true)
}

data class BeszelAlertEvent(
    val id: String,
    val name: String,
    val systemId: String,
    val systemName: String,
    val monitorName: String,
    val value: Double,
    val created: Instant?,
    val resolved: Instant?,
) {
    val isActive: Boolean get() = resolved == null
}

data class BeszelAlertRule(
    val id: String,
    val name: String,
    val systemId: String,
    val value: Double,
    val min: Int,
    val triggered: Boolean,
)

data class BeszelHubInfo(
    val version: String,
    val checkUpdates: Boolean,
)

sealed interface BeszelAuthResult {
    data class Success(val session: BeszelSession) : BeszelAuthResult
    data class MfaRequired(val mfaId: String, val otpId: String, val email: String) : BeszelAuthResult
    data class Error(val message: String) : BeszelAuthResult
}

// --- JSON & PocketBase Date Helpers ---

internal fun JsonElement.asDoubleValue(): Double =
    (this as? JsonPrimitive)?.doubleOrNull ?: 0.0

internal fun JsonElement.asIntValue(): Int =
    (this as? JsonPrimitive)?.intOrNull ?: (this as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.doubleValue(key: String): Double =
    doubleOrNull(key) ?: 0.0

internal fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()

internal fun JsonObject.intValue(key: String): Int =
    intOrNull(key) ?: 0

internal fun JsonObject.longValue(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L

internal fun JsonObject.booleanValue(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

internal fun JsonObject.objectOrNull(key: String): JsonObject? =
    this[key] as? JsonObject

internal fun JsonObject.arrayOrNull(key: String): JsonArray? =
    this[key] as? JsonArray

internal fun JsonObject.doubleList(key: String): List<Double> =
    (this[key] as? JsonArray)?.map { it.asDoubleValue() } ?: emptyList()

private val PB_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

internal fun formatPbTimestamp(instant: Instant): String =
    PB_DATE_FORMATTER.format(instant)

internal fun parsePbInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(raw)
    }.getOrElse {
        runCatching {
            val normalized = raw.trim().replace(' ', 'T')
            val withZone = if (normalized.endsWith("Z") || normalized.contains("+")) normalized else "${normalized}Z"
            OffsetDateTime.parse(withZone).toInstant()
        }.getOrNull()
    }
}

internal fun BeszelChartRange.sinceInstant(): Instant =
    Instant.now().minus(Duration.ofMinutes(spanMinutes))
