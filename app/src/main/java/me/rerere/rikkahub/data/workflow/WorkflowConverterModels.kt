package me.rerere.rikkahub.data.workflow

const val PDF_CONVERSION_TYPE = "pdf_to_md"
const val MAX_CONVERSION_PROMPT_CHARS = 12_000

enum class PdfConversionProvider(val wireValue: String, val label: String) {
    GEMINI("gemini", "Gemini"),
    MODELFLARE("modelflare", "ModelFlare");

    companion object {
        fun fromWire(value: String): PdfConversionProvider =
            entries.firstOrNull { it.wireValue.equals(value.trim(), ignoreCase = true) } ?: GEMINI
    }
}

enum class PdfJobStatus(
    val wireValue: String,
    val label: String,
    val tone: StatusTone,
) {
    QUEUED("queued", "排队中", StatusTone.INFO),
    RUNNING("running", "转换中", StatusTone.WARNING),
    SUCCEEDED("succeeded", "已完成", StatusTone.SUCCESS),
    FAILED("failed", "失败", StatusTone.DANGER),
    CANCELED("canceled", "已取消", StatusTone.NEUTRAL);

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING

    val isTerminal: Boolean
        get() = this == SUCCEEDED || this == FAILED || this == CANCELED

    companion object {
        fun fromWire(value: String): PdfJobStatus =
            entries.firstOrNull { it.wireValue.equals(value.trim(), ignoreCase = true) } ?: QUEUED
    }
}

data class PdfConversionOptions(
    val imagesPerRequest: Int = 1,
    val concurrency: Int = 5,
    val provider: PdfConversionProvider = PdfConversionProvider.GEMINI,
    val model: String = "gemini-3.5-flash-lite",
    val reasoningEffort: String = "",
    val maxOutputTokens: Int = 0,
    val thinkingLevel: String = "high",
    val dpi: Int = 240,
    val imageFormat: String = "png",
    val jpegQuality: Int = 95,
    val verificationPasses: Int = 0,
    val mediaResolution: String = "ultra_high",
    val rpmPerKey: Int = 15,
    val rpdPerKey: Int = 500,
    val svgEnabled: Boolean = true,
    val svgProvider: PdfConversionProvider = PdfConversionProvider.GEMINI,
    val svgModel: String = "gemini-3.5-flash-lite",
    val svgPrompt: String = DEFAULT_PDF_SVG_PROMPT,
)

data class FileConversionConfig(
    val id: String,
    val owner: String,
    val conversionType: String = PDF_CONVERSION_TYPE,
    val name: String = "默认配置",
    val systemPrompt: String = DEFAULT_PDF_SYSTEM_PROMPT,
    val options: PdfConversionOptions = PdfConversionOptions(),
    val revision: Int = 1,
    val created: String = "",
    val updated: String = "",
)

data class PdfJob(
    val id: String,
    val owner: String,
    val title: String,
    val sourceUrl: String,
    val outputName: String,
    val prompt: String,
    val configId: String,
    val configRevision: Int,
    val systemPrompt: String,
    val status: PdfJobStatus,
    val file: String,
    val fileSize: Long,
    val githubRunId: String,
    val error: String,
    val created: String,
    val updated: String,
    val startedAt: String,
    val finishedAt: String,
    val cancelRequested: Boolean,
    val rerunOf: String,
    val startPage: Int?,
    val endPage: Int?,
    val adjacentPages: Boolean,
    val modelSummary: String = "",
    val dpiSummary: Int? = null,
) {
    val displayTitle: String
        get() = title.ifBlank { outputName }.ifBlank { "PDF 转换任务 $id" }

    val pageRangeText: String
        get() = when {
            startPage != null && endPage != null ->
                if (startPage == endPage) "第 $startPage 页" else "第 $startPage–$endPage 页"
            startPage != null -> "第 $startPage 页起"
            endPage != null -> "前 $endPage 页"
            else -> ""
        }

    val githubActionsUrl: String?
        get() = githubRunId.trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.let { "https://github.com/ActiveInsighter/file-converter-ai/actions/runs/$it" }
}

const val DEFAULT_PDF_SVG_PROMPT = """请将这张图片中的图形、图表、示意图、流程图或几何图形，忠实地重写为高质量、自包含的矢量 SVG 代码。

要求：
1. 准确还原原图中的所有几何形状、线条、箭头、节点、图例、标注文字、数字、数学符号、坐标轴、刻度、阴影或颜色关系，保持原有的空间布局与比例。
2. 使用标准的 SVG 矢量元素（如 <path>、<rect>、<circle>、<ellipse>、<line>、<polygon>、<polyline>、<text>、<g>、<defs>、<marker> 等），不要使用 <image> 嵌入位图。
3. 根元素 <svg> 必须包含 xmlns="http://www.w3.org/2000/svg" 和合理的 viewBox="0 0 W H"（与原图宽高比一致），确保清晰可读、线条锐利、文字不重叠不截断。
4. 如原图背景为纯白或浅色，可使用白色背景矩形或透明背景，并保证深色线条与文字具有高对比度。
5. 直接输出完整的 <svg>...</svg> 代码，不要输出任何解释、说明或多余文字。"""

const val DEFAULT_PDF_SYSTEM_PROMPT = """请按图片原始顺序逐页、忠实地把这些 PDF 页面转写为 Markdown。这是高精度文档转录任务，不是总结、改写或解题任务。

要求：
1. 不要遗漏正文、标题、列表、表格、公式、题目、选项、注释、评分等有效内容，并严格保持原始顺序。
2. 数学内容必须逐字符核对图片：重点检查正负号、等号/不等号、上下标、根号、分数、积分/求和上下限、导数阶数、矩阵元素、向量转置、希腊字母和括号。不要仅凭上下文猜公式。
3. 不要自行修正原文、补全推导或加入解释。看不清时写 [无法辨认]，不要编造。
4. 数学公式使用标准 LaTeX。行内公式只用成对的 ${'$'}...${'$'}；多行公式、aligned、cases、matrix 等必须使用成对的 ${'$'}${'$'}...${'$'}${'$'}，禁止用单个 ${'$'} 跨多行包裹。
5. 不要输出处理备注、模型解释、占位说明或类似“此行视具体排版”的元信息。
6. 删除可确认的重复页眉页脚、纯页码、扫描水印和装饰，不得删除图题、表题、脚注、正文注释或正文中的重复数字。
7. 表格尽量转换为 Markdown 表格；无法可靠转换时按原有阅读顺序保留文本，不要虚构表格结构。
8. 输出前自行复核一遍图片与 Markdown，尤其复核所有数学公式和数字。
9. 保留原文语言和措辞，不能翻译、概括或把题目改为答案。多栏按栏阅读，题号、选项、单位、否定词和限定条件逐项保留。
10. 直接输出 Markdown，不要使用包裹整个回答的代码围栏；原文的代码块应保留。"""
