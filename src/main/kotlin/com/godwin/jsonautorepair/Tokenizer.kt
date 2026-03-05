package com.godwin.jsonautorepair

/**
 * Tokenizes a JSON string into a list of tokens, tolerating malformed input.
 * Handles single/double quotes, comments, unquoted strings, and multiline input.
 */
internal class Tokenizer(private val input: String) {

    private var pos = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < input.length) {
            skipWhitespace()
            if (pos >= input.length) break
            val ch = input[pos]
            val token = when (ch) {
                '{' -> Token(TokenType.LEFT_BRACE, "{", pos).also { pos++ }
                '}' -> Token(TokenType.RIGHT_BRACE, "}", pos).also { pos++ }
                '[' -> Token(TokenType.LEFT_BRACKET, "[", pos).also { pos++ }
                ']' -> Token(TokenType.RIGHT_BRACKET, "]", pos).also { pos++ }
                ':' -> Token(TokenType.COLON, ":", pos).also { pos++ }
                ',' -> Token(TokenType.COMMA, ",", pos).also { pos++ }
                '"' -> readString()
                '\'' -> readSingleQuotedString()
                in '0'..'9', '-' -> readNumber()
                't' -> readLiteral("true", TokenType.TRUE) ?: readUnquotedString()
                'f' -> readLiteral("false", TokenType.FALSE) ?: readUnquotedString()
                'n' -> readLiteral("null", TokenType.NULL) ?: readUnquotedString()
                'T' -> readLiteral("True", TokenType.TRUE) ?: readLiteral("TRUE", TokenType.TRUE) ?: readUnquotedString()
                'F' -> readLiteral("False", TokenType.FALSE) ?: readLiteral("FALSE", TokenType.FALSE) ?: readUnquotedString()
                'N' -> readLiteral("None", TokenType.NULL) ?: readLiteral("NULL", TokenType.NULL) ?: readUnquotedString()
                'u' -> readLiteral("undefined", TokenType.NULL) ?: readUnquotedString()
                else -> readUnquotedString()
            }
            tokens.add(token)
        }
        tokens.add(Token(TokenType.EOF, "", pos))
        return tokens
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
        // Skip single-line comments
        if (pos < input.length - 1 && input[pos] == '/' && input[pos + 1] == '/') {
            while (pos < input.length && input[pos] != '\n') pos++
            skipWhitespace()
        }
        // Skip block comments
        if (pos < input.length - 1 && input[pos] == '/' && input[pos + 1] == '*') {
            pos += 2
            while (pos < input.length - 1 && !(input[pos] == '*' && input[pos + 1] == '/')) pos++
            if (pos < input.length - 1) pos += 2
            skipWhitespace()
        }
    }

    private fun readString(): Token {
        val start = pos
        pos++ // skip opening "
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != '"') {
            if (input[pos] == '\n' || input[pos] == '\r') break
            if (input[pos] == '\\') {
                sb.append(input[pos]); pos++
                if (pos < input.length) { sb.append(input[pos]); pos++ }
            } else {
                sb.append(input[pos]); pos++
            }
        }
        if (pos < input.length && input[pos] == '"') pos++
        return Token(TokenType.STRING, sb.toString(), start)
    }

    private fun readSingleQuotedString(): Token {
        val start = pos
        pos++ // skip opening '
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != '\'') {
            if (input[pos] == '\n' || input[pos] == '\r') break
            if (input[pos] == '\\') {
                sb.append(input[pos]); pos++
                if (pos < input.length) { sb.append(input[pos]); pos++ }
            } else {
                sb.append(input[pos]); pos++
            }
        }
        if (pos < input.length && input[pos] == '\'') pos++
        return Token(TokenType.STRING, sb.toString(), start)
    }

    private fun readNumber(): Token {
        val start = pos
        val sb = StringBuilder()
        if (pos < input.length && input[pos] == '-') { sb.append('-'); pos++ }
        while (pos < input.length && input[pos].isDigit()) { sb.append(input[pos]); pos++ }
        if (pos < input.length && input[pos] == '.') {
            sb.append('.'); pos++
            while (pos < input.length && input[pos].isDigit()) { sb.append(input[pos]); pos++ }
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            sb.append(input[pos]); pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) { sb.append(input[pos]); pos++ }
            while (pos < input.length && input[pos].isDigit()) { sb.append(input[pos]); pos++ }
        }
        return Token(TokenType.NUMBER, sb.toString(), start)
    }

    private fun readLiteral(expected: String, type: TokenType): Token? {
        if (pos + expected.length <= input.length &&
            input.substring(pos, pos + expected.length) == expected) {
            val endPos = pos + expected.length
            if (endPos < input.length && (input[endPos].isLetterOrDigit() || input[endPos] == '_')) {
                return null
            }
            val token = Token(type, expected, pos)
            pos += expected.length
            return token
        }
        return null
    }

    private fun readUnquotedString(): Token {
        val start = pos
        val sb = StringBuilder()
        while (pos < input.length) {
            val ch = input[pos]
            if (ch in setOf('{', '}', '[', ']', ':', ',', '"', '\'') || ch.isWhitespace()) break
            sb.append(ch); pos++
        }
        if (sb.isEmpty()) {
            pos++
            return Token(TokenType.UNKNOWN, input[start].toString(), start)
        }
        return Token(TokenType.STRING, sb.toString(), start)
    }
}
