package com.davidzhu.wantnote.transfer

/**
 * 0.16.5：离线纯 Kotlin LaTeX 数学公式渲染格式化器（JVM 可单测，零 WebView/CDN 依赖）。
 *
 * 将常见 LaTeX 数学语法（希腊字母、运算符、箭头、集合符号、`\frac{a}{b}`、`\sqrt{x}`、
 * 上下标 `^{...}` / `_{...}`、`\sum` / `\int` / `\lim` / `\infty` / `\text{...}` 等）
 * 转换为高可读性的数学符号排版，配合 Compose 衬线斜体公式块与行内公式样式渲染。
 */
object LatexMath {
    private val blockRegex = Regex("""^\s*\$\$([\s\S]+?)\$\$\s*$""")

    fun extractBlockFormula(text: String): String? {
        return blockRegex.matchEntire(text.trim())?.groupValues?.get(1)?.trim()
    }

    fun format(rawLatex: String): String {
        var s = rawLatex.trim()
        if (s.isEmpty()) return ""

        // 1. 剥离环境壳与常见格式命令
        s = s.replace(Regex("""\\(?:left|right|big|Big|bigg|Bigg)\b"""), "")
        s = s.replace(Regex("""\\(?:displaystyle|textstyle|scriptstyle)\b"""), "")
        s = s.replace(Regex("""\\(?:quad|qquad|,|;|!)\b"""), " ")
        s = s.replace("\\\\", "\n")

        // 2. 递归展开 \frac{a}{b}、\sqrt{x}、\text{...}、\mathbf{...} 等带参数命令
        s = replaceBinaryCommand(s, "\\frac") { num, den -> "($num)/($den)" }
        s = replaceBinaryCommand(s, "\\dfrac") { num, den -> "($num)/($den)" }
        s = replaceBinaryCommand(s, "\\tfrac") { num, den -> "($num)/($den)" }
        s = replaceUnaryCommand(s, "\\sqrt") { inner -> "√($inner)" }
        s = replaceUnaryCommand(s, "\\text") { inner -> inner }
        s = replaceUnaryCommand(s, "\\mathrm") { inner -> inner }
        s = replaceUnaryCommand(s, "\\mathbf") { inner -> inner }
        s = replaceUnaryCommand(s, "\\mathbb") { inner ->
            when (inner.trim()) {
                "R" -> "ℝ"
                "N" -> "ℕ"
                "Z" -> "ℤ"
                "Q" -> "ℚ"
                "C" -> "ℂ"
                else -> inner
            }
        }
        s = replaceUnaryCommand(s, "\\bar") { inner -> "$inner̄" }
        s = replaceUnaryCommand(s, "\\hat") { inner -> "$inner̂" }
        s = replaceUnaryCommand(s, "\\vec") { inner -> "$inner⃗" }

        // 3. 替换希腊字母与数学符号
        for ((cmd, sym) in SYMBOLS) {
            s = s.replace(Regex("""${Regex.escape(cmd)}(?![A-Za-z])"""), sym)
        }

        // 4. 上标 ^{...} / ^x 与下标 _{...} / _x
        s = replaceScripts(s, '^', SUPERSCRIPTS)
        s = replaceScripts(s, '_', SUBSCRIPTS)

        // 5. 清理多余花括号与多余空白
        s = s.replace("{", "").replace("}", "")
        s = s.replace(Regex("""[ \t]+"""), " ").trim()
        return s
    }

    private fun replaceUnaryCommand(input: String, cmd: String, transform: (String) -> String): String {
        var s = input
        var guard = 0
        while (guard++ < 32) {
            val idx = s.indexOf(cmd)
            if (idx < 0) break
            val after = idx + cmd.length
            val arg = readBracedArg(s, after) ?: break
            val replacement = transform(format(arg.content))
            s = s.substring(0, idx) + replacement + s.substring(arg.nextIndex)
        }
        return s
    }

    private fun replaceBinaryCommand(input: String, cmd: String, transform: (String, String) -> String): String {
        var s = input
        var guard = 0
        while (guard++ < 32) {
            val idx = s.indexOf(cmd)
            if (idx < 0) break
            val first = readBracedArg(s, idx + cmd.length) ?: break
            val second = readBracedArg(s, first.nextIndex) ?: break
            val replacement = transform(format(first.content), format(second.content))
            s = s.substring(0, idx) + replacement + s.substring(second.nextIndex)
        }
        return s
    }

    private data class BracedArg(val content: String, val nextIndex: Int)

    private fun readBracedArg(s: String, start: Int): BracedArg? {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length || s[i] != '{') return null
        var depth = 0
        val begin = i + 1
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return BracedArg(s.substring(begin, i), i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    private fun replaceScripts(input: String, marker: Char, map: Map<Char, Char>): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            if (input[i] == marker && i + 1 < input.length) {
                if (input[i + 1] == '{') {
                    val arg = readBracedArg(input, i + 1)
                    if (arg != null) {
                        val mapped = arg.content.map { map[it] }
                        if (mapped.all { it != null }) {
                            mapped.forEach { out.append(it) }
                        } else {
                            out.append(marker).append('(').append(arg.content).append(')')
                        }
                        i = arg.nextIndex
                        continue
                    }
                } else {
                    val ch = input[i + 1]
                    val mapped = map[ch]
                    if (mapped != null) {
                        out.append(mapped)
                        i += 2
                        continue
                    }
                }
            }
            out.append(input[i])
            i++
        }
        return out.toString()
    }

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'T' to 'ᵀ',
    )

    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ',
        's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
    )

    private val SYMBOLS = listOf(
        "\\leftrightarrow" to "↔",
        "\\Leftrightarrow" to "⇔",
        "\\rightarrow" to "→",
        "\\Rightarrow" to "⇒",
        "\\leftarrow" to "←",
        "\\Leftarrow" to "⇐",
        "\\mapsto" to "↦",
        "\\subseteq" to "⊆",
        "\\supseteq" to "⊇",
        "\\subset" to "⊂",
        "\\supset" to "⊃",
        "\\notin" to "∉",
        "\\infty" to "∞",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\neq" to "≠",
        "\\leq" to "≤",
        "\\geq" to "≥",
        "\\le" to "≤",
        "\\ge" to "≥",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\cdots" to "⋯",
        "\\ldots" to "…",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\oint" to "∮",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\emptyset" to "∅",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\in" to "∈",
        "\\to" to "→",
        "\\deg" to "°",
        "\\angle" to "∠",
        "\\perp" to "⊥",
        "\\parallel" to "∥",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\varepsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\phi" to "φ",
        "\\varphi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\log" to "log",
        "\\ln" to "ln",
        "\\lim" to "lim",
        "\\exp" to "exp",
    )
}
