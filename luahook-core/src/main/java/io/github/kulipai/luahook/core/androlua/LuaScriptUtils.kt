package io.github.kulipai.luahook.core.androlua

object LuaScriptUtils {

    /**
     * Function extraction result
     */
    data class FunctionExtractionResult(
        val functionCode: String,
        val functionStartLine: Int // Line number in original script (1-based)
    )

    /**
     * Extract function with label from Lua code
     * Supported formats: ::label、@label、::label::、@label@、::label@、@label::
     */
    fun extractLuaFunctionByLabel(luaCode: String, label: String): FunctionExtractionResult? {
        val lines = luaCode.split('\n')
        val labelPattern = createLabelPattern(label)

        // Find label position
        var labelLineIndex = -1
        for (i in lines.indices) {
            if (labelPattern.matches(lines[i].trim())) {
                labelLineIndex = i
                break
            }
        }

        if (labelLineIndex == -1) {
            return null // Label not found
        }

        // Find function start after label
        var functionStartLine = -1

        for (i in (labelLineIndex + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isNotEmpty()) {
                val functionMatch = Regex("""^\s*function\s+\w+\s*\(.*?\)\s*$""").find(lines[i])
                if (functionMatch != null) {
                    functionStartLine = i
                    break
                }
            }
        }

        if (functionStartLine == -1) {
            return null // Function not found
        }

        // Extract complete function
        val functionCode = extractCompleteFunction(lines, functionStartLine)

        return FunctionExtractionResult(
            functionCode = functionCode,
            functionStartLine = functionStartLine + 1 // Convert to 1-based line number
        )
    }

    /**
     * Create label matching pattern
     */
    private fun createLabelPattern(label: String): Regex {
        val escapedLabel = Regex.escape(label)
        // Matches ::label、@label、::label::、@label@、::label@、@label::
        return Regex("^\\s*[:@]{1,2}$escapedLabel[:@]{0,2}\\s*$")
    }

    /**
     * Extract complete function, handling nested structures
     */
    private fun extractCompleteFunction(lines: List<String>, startLine: Int): String {
        val result = mutableListOf<String>()
        var depth = 0
        var i = startLine

        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()

            result.add(line)

            // Calculate depth change
            depth += countOpeningKeywords(trimmedLine)
            depth -= countClosingKeywords(trimmedLine)

            // If it's the first line (function declaration), depth should be 1
            if (i == startLine) {
                depth = 1
            }

            // If depth returns to 0, function ends
            if (depth == 0) {
                break
            }

            i++
        }

        return result.joinToString("\n")
    }

    /**
     * Count opening keywords
     */
    private fun countOpeningKeywords(line: String): Int {
        var count = 0
        val keywords = listOf("function", "if", "for", "while", "repeat", "do")

        // Remove strings and comments
        val cleanLine = removeStringsAndComments(line)

        for (keyword in keywords) {
            // Use word boundary to avoid matching keywords inside variable names
            val pattern = Regex("\\b$keyword\\b")
            count += pattern.findAll(cleanLine).count()
        }

        return count
    }

    /**
     * Count closing keywords
     */
    private fun countClosingKeywords(line: String): Int {
        var count = 0
        val keywords = listOf("end", "until")

        // Remove strings and comments
        val cleanLine = removeStringsAndComments(line)

        for (keyword in keywords) {
            // Use word boundary
            val pattern = Regex("\\b$keyword\\b")
            count += pattern.findAll(cleanLine).count()
        }

        return count
    }

    /**
     * Remove strings and comments to avoid miscounting keywords
     */
    private fun removeStringsAndComments(line: String): String {
        var result = line

        // Remove single line comments
        val commentIndex = result.indexOf("--")
        if (commentIndex != -1) {
            result = result.substring(0, commentIndex)
        }

        // Remove strings (simplified, only handles double and single quotes)
        result = result.replace(Regex("\"[^\"]*\""), "")
        result = result.replace(Regex("'[^']*'"), "")

        return result
    }
    
    fun getFunctionName(functionString: String): String? {
        // Regex to match function name after 'function '
        // Function name consists of letters, numbers, underscores, cannot start with number
        val functionNamePattern = Regex("function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(.*\\)")

        val matchResult = functionNamePattern.find(functionString)

        // Return the first capture group (function name)
        return matchResult?.groups?.get(1)?.value
    }

    fun simplifyLuaError(raw: String, funcLine: Int): String {
        val lines = raw.lines()

        // 1. Prioritize extracting the first real error message (not traceback)
        val primaryErrorLine = lines.firstOrNull { it.trim().matches(Regex(""".*:\d+ .+""")) }

        if (primaryErrorLine != null) {
            val match = Regex(""".*:(\d+) (.+)""").find(primaryErrorLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line ${lineNum.toInt() + funcLine - 1}: $msg"
            }
        }

        // 2. Fallback to extracting from traceback
        val fallbackLine = lines.find { it.trim().matches(Regex(""".*:\d+: .*""")) }
        if (fallbackLine != null) {
            val match = Regex(""".*:(\d+): (.+)""").find(fallbackLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line ${lineNum.toInt() + funcLine - 1}: $msg"
            }
        }

        return raw.lines().firstOrNull()?.take(100) ?: "Unknown Error"
    }
}
