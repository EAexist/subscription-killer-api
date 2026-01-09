package com.matchalab.subscription_killer_api.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

fun generateTokenReport(metrics: List<TokenMetrics>, title: String? = null) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
    val reportDir = Paths.get("build/reports/token-usage").toFile().apply { mkdirs() }
    val reportFile = File(reportDir, "usage_report_$timestamp.md")

    val markdownContent = StringBuilder().apply {
        appendLine("# Token Usage Evaluation Report${title?.let { ": $it" } ?: ""}")
        appendLine("\nGenerated on: ${LocalDateTime.now()}")
        appendLine("\n| Task ID | Prompt | Cached | Thinking | Compl. | Total |")
        appendLine("|:---|---:|---:|---:|---:|---:|") // Markdown table alignment

        metrics.forEach { m ->
            appendLine("| ${m.taskId} | ${m.prompt} | ${m.cached} | ${m.thinking} | ${m.completion} | ${m.total} |")
        }
    }.toString()

    reportFile.writeText(markdownContent)

    logger.debug { "Report generated at: ${reportFile.absolutePath}" }
}