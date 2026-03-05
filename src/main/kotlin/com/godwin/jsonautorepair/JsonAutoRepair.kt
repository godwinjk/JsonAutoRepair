package com.godwin.jsonautorepair

/**
 * Iteratively repairs malformed JSON strings by tokenizing, analyzing structure,
 * and fixing issues up to [maxIterations] times.
 *
 * Handles: missing braces/brackets, trailing/missing commas, single quotes,
 * unquoted keys, missing colons, comments, boolean/null variants (True, False,
 * None, undefined), multiline strings, and deeply nested structural errors.
 *
 * Zero external dependencies.
 *
 * @param maxIterations maximum repair passes (default 10)
 */
class JsonAutoRepair(private val maxIterations: Int = 10) {

    data class Result(
        val output: String,
        val iterations: Int,
        val wasValid: Boolean
    )

    fun repair(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result("null", 1, false)

        if (isValidJson(trimmed)) return Result(trimmed, 0, true)

        var current = trimmed
        var previous = ""
        for (i in 1..maxIterations) {
            current = repairPass(current)
            if (isValidJson(current)) return Result(current, i, false)
            if (current == previous) break
            previous = current
        }
        return Result(current, maxIterations, false)
    }

    private fun repairPass(input: String): String {
        val tokens = Tokenizer(input).tokenize()
        val fixed = fixTokenStream(tokens)
        return reconstruct(fixed)
    }

    private fun fixTokenStream(tokens: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        val stack = mutableListOf<TokenType>()
        var i = 0

        while (i < tokens.size) {
            val token = tokens[i]

            when (token.type) {
                TokenType.EOF -> break
                TokenType.UNKNOWN -> { i++; continue }

                TokenType.LEFT_BRACE -> {
                    if (isInObject(stack) && expectingValue(result)) {
                        result.add(Token(TokenType.COLON, ":", token.position))
                    } else {
                        maybeInsertComma(result, stack)
                    }
                    result.add(token)
                    stack.add(TokenType.LEFT_BRACE)
                    i++
                }

                TokenType.LEFT_BRACKET -> {
                    if (isInObject(stack) && expectingValue(result)) {
                        result.add(Token(TokenType.COLON, ":", token.position))
                    } else {
                        maybeInsertComma(result, stack)
                    }
                    result.add(token)
                    stack.add(TokenType.LEFT_BRACKET)
                    i++
                }

                TokenType.RIGHT_BRACE -> {
                    removeTrailingComma(result)
                    if (result.isNotEmpty() && result.last().type == TokenType.COLON) {
                        result.add(Token(TokenType.NULL, "null", token.position))
                    }
                    if (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACE) {
                        stack.removeLast(); result.add(token)
                    } else if (stack.contains(TokenType.LEFT_BRACE)) {
                        while (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACKET) {
                            result.add(Token(TokenType.RIGHT_BRACKET, "]", token.position))
                            stack.removeLast()
                        }
                        if (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACE) {
                            stack.removeLast(); result.add(token)
                        }
                    }
                    i++
                }

                TokenType.RIGHT_BRACKET -> {
                    removeTrailingComma(result)
                    if (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACKET) {
                        stack.removeLast(); result.add(token)
                    } else if (stack.contains(TokenType.LEFT_BRACKET)) {
                        while (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACE) {
                            removeTrailingComma(result)
                            if (result.isNotEmpty() && result.last().type == TokenType.COLON) {
                                result.add(Token(TokenType.NULL, "null", token.position))
                            }
                            result.add(Token(TokenType.RIGHT_BRACE, "}", token.position))
                            stack.removeLast()
                        }
                        if (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACKET) {
                            stack.removeLast(); result.add(token)
                        }
                    }
                    i++
                }

                TokenType.COMMA -> {
                    if (result.isNotEmpty() && result.last().type == TokenType.COMMA) { i++; continue }
                    if (result.isNotEmpty() && result.last().type in setOf(TokenType.LEFT_BRACE, TokenType.LEFT_BRACKET)) { i++; continue }
                    result.add(token); i++
                }

                TokenType.COLON -> {
                    if (result.isEmpty() || result.last().type !in setOf(TokenType.STRING, TokenType.NUMBER)) { i++; continue }
                    result.add(token); i++
                }

                TokenType.STRING -> {
                    handleValue(token, tokens, i, result, stack); i++
                }

                TokenType.NUMBER, TokenType.TRUE, TokenType.FALSE, TokenType.NULL -> {
                    maybeInsertComma(result, stack)
                    if (isInObject(stack) && expectingKey(result)) {
                        result.add(Token(TokenType.STRING, token.value, token.position))
                        if (i + 1 < tokens.size && tokens[i + 1].type != TokenType.COLON) {
                            result.add(Token(TokenType.COLON, ":", token.position))
                        }
                    } else {
                        result.add(token)
                    }
                    i++
                }
            }
        }

        // Close unclosed containers
        while (stack.isNotEmpty()) {
            val open = stack.removeLast()
            removeTrailingComma(result)
            if (result.isNotEmpty() && result.last().type == TokenType.COLON) {
                result.add(Token(TokenType.NULL, "null", tokens.last().position))
            }
            if (open == TokenType.LEFT_BRACE) {
                result.add(Token(TokenType.RIGHT_BRACE, "}", tokens.last().position))
            } else {
                result.add(Token(TokenType.RIGHT_BRACKET, "]", tokens.last().position))
            }
        }

        if (result.isEmpty()) return listOf(Token(TokenType.NULL, "null", 0))
        return wrapMultipleTopLevel(result)
    }

    private fun wrapMultipleTopLevel(tokens: List<Token>): List<Token> {
        var depth = 0
        var topLevelCount = 0
        for (token in tokens) {
            when (token.type) {
                TokenType.LEFT_BRACE, TokenType.LEFT_BRACKET -> { if (depth == 0) topLevelCount++; depth++ }
                TokenType.RIGHT_BRACE, TokenType.RIGHT_BRACKET -> depth--
                else -> {
                    if (depth == 0 && token.type in VALUE_TYPES) topLevelCount++
                }
            }
        }
        if (topLevelCount <= 1) return tokens

        val wrapped = mutableListOf<Token>()
        wrapped.add(Token(TokenType.LEFT_BRACKET, "[", 0))
        depth = 0
        var needsComma = false
        for (token in tokens) {
            if (depth == 0 && needsComma) {
                val isValue = token.type in VALUE_TYPES || token.type == TokenType.LEFT_BRACE || token.type == TokenType.LEFT_BRACKET
                if (isValue) wrapped.add(Token(TokenType.COMMA, ",", token.position))
            }
            wrapped.add(token)
            when (token.type) {
                TokenType.LEFT_BRACE, TokenType.LEFT_BRACKET -> depth++
                TokenType.RIGHT_BRACE, TokenType.RIGHT_BRACKET -> { depth--; if (depth == 0) needsComma = true }
                else -> { if (depth == 0 && token.type in VALUE_TYPES) needsComma = true }
            }
        }
        wrapped.add(Token(TokenType.RIGHT_BRACKET, "]", 0))
        return wrapped
    }

    private fun handleValue(token: Token, tokens: List<Token>, index: Int, result: MutableList<Token>, stack: MutableList<TokenType>) {
        if (token.value.isEmpty() && isInObject(stack) && expectingKey(result)) return

        if (isInArray(stack) && index + 1 < tokens.size && tokens[index + 1].type == TokenType.COLON) {
            if (stack.contains(TokenType.LEFT_BRACE)) {
                while (stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACKET) {
                    removeTrailingComma(result)
                    result.add(Token(TokenType.RIGHT_BRACKET, "]", token.position))
                    stack.removeLast()
                }
                maybeInsertComma(result, stack)
                if (isInObject(stack) && expectingKey(result)) { result.add(token); return }
            }
        }

        maybeInsertComma(result, stack)

        if (isInObject(stack) && expectingKey(result)) {
            result.add(token)
            if (index + 1 < tokens.size && tokens[index + 1].type != TokenType.COLON) {
                result.add(Token(TokenType.COLON, ":", token.position))
            }
        } else if (isInObject(stack) && result.isNotEmpty() && result.last().type == TokenType.COLON) {
            result.add(token)
        } else {
            result.add(token)
        }
    }

    private fun isInObject(stack: List<TokenType>) = stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACE
    private fun isInArray(stack: List<TokenType>) = stack.isNotEmpty() && stack.last() == TokenType.LEFT_BRACKET

    private fun expectingKey(result: List<Token>): Boolean {
        if (result.isEmpty()) return false
        return result.last().type in setOf(TokenType.LEFT_BRACE, TokenType.COMMA)
    }

    private fun expectingValue(result: List<Token>): Boolean {
        if (result.size < 2) return false
        return result.last().type == TokenType.STRING &&
                result[result.size - 2].type in setOf(TokenType.LEFT_BRACE, TokenType.COMMA)
    }

    private fun maybeInsertComma(result: MutableList<Token>, stack: List<TokenType>) {
        if (result.isEmpty()) return
        if (result.last().type in COMMA_PRECEDING_TYPES && stack.isNotEmpty()) {
            result.add(Token(TokenType.COMMA, ",", result.last().position))
        }
    }

    private fun removeTrailingComma(result: MutableList<Token>) {
        if (result.isNotEmpty() && result.last().type == TokenType.COMMA) result.removeLast()
    }

    private fun reconstruct(tokens: List<Token>): String {
        val sb = StringBuilder()
        var prevType: TokenType? = null
        for (token in tokens) {
            if (prevType == TokenType.COLON || prevType == TokenType.COMMA) sb.append(' ')
            when (token.type) {
                TokenType.STRING -> { sb.append('"'); sb.append(escapeString(token.value)); sb.append('"') }
                TokenType.NUMBER -> sb.append(fixNumber(token.value))
                TokenType.TRUE -> sb.append("true")
                TokenType.FALSE -> sb.append("false")
                TokenType.NULL -> sb.append("null")
                else -> sb.append(token.value)
            }
            prevType = token.type
        }
        return sb.toString()
    }

    private fun escapeString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                val next = s[i + 1]
                when (next) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> { sb.append('\\'); sb.append(next); i += 2; continue }
                    'u' -> {
                        sb.append("\\u"); i += 2
                        var count = 0
                        while (count < 4 && i < s.length && s[i].isLetterOrDigit()) { sb.append(s[i]); i++; count++ }
                        continue
                    }
                    '\'' -> { sb.append('\''); i += 2; continue }
                    else -> { sb.append('\\'); sb.append(next); i += 2; continue }
                }
            }
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (ch.code < 0x20) sb.append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    else sb.append(ch)
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun fixNumber(value: String): String {
        var v = value
        if (v == "-" || v == "+") return "0"
        if (v.startsWith(".")) v = "0$v"
        if (v.startsWith("-.")) v = "-0${v.substring(1)}"
        if (v.endsWith(".")) v = "${v}0"
        return try { v.toDouble(); v } catch (_: NumberFormatException) { "0" }
    }

    private fun isValidJson(s: String): Boolean {
        return try { JsonValidator(s).validate(); true } catch (_: Exception) { false }
    }

    companion object {
        private val VALUE_TYPES = setOf(TokenType.STRING, TokenType.NUMBER, TokenType.TRUE, TokenType.FALSE, TokenType.NULL)
        private val COMMA_PRECEDING_TYPES = setOf(
            TokenType.STRING, TokenType.NUMBER, TokenType.TRUE,
            TokenType.FALSE, TokenType.NULL, TokenType.RIGHT_BRACE, TokenType.RIGHT_BRACKET
        )
    }
}
