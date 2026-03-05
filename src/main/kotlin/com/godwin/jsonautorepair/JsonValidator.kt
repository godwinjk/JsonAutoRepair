package com.godwin.jsonautorepair

/**
 * Strict JSON validator using recursive descent parsing.
 * Zero external dependencies. Throws [JsonValidationException] on invalid JSON.
 */
class JsonValidator(private val input: String) {

    class JsonValidationException(message: String) : Exception(message)

    private var pos = 0

    fun validate() {
        skipWhitespace()
        if (pos >= input.length) throw JsonValidationException("Empty input")
        parseValue()
        skipWhitespace()
        if (pos < input.length) throw JsonValidationException("Unexpected trailing content at $pos")
    }

    private fun parseValue() {
        skipWhitespace()
        if (pos >= input.length) throw JsonValidationException("Unexpected end of input")
        when (input[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true")
            'f' -> parseLiteral("false")
            'n' -> parseLiteral("null")
            else -> {
                if (input[pos] == '-' || input[pos].isDigit()) parseNumber()
                else throw JsonValidationException("Unexpected character '${input[pos]}' at $pos")
            }
        }
    }

    private fun parseObject() {
        expect('{')
        skipWhitespace()
        if (pos < input.length && input[pos] == '}') { pos++; return }
        while (true) {
            skipWhitespace()
            if (pos >= input.length || input[pos] != '"')
                throw JsonValidationException("Expected string key at $pos")
            parseString()
            skipWhitespace()
            expect(':')
            parseValue()
            skipWhitespace()
            if (pos >= input.length) throw JsonValidationException("Unexpected end in object")
            if (input[pos] == '}') { pos++; return }
            expect(',')
        }
    }

    private fun parseArray() {
        expect('[')
        skipWhitespace()
        if (pos < input.length && input[pos] == ']') { pos++; return }
        while (true) {
            parseValue()
            skipWhitespace()
            if (pos >= input.length) throw JsonValidationException("Unexpected end in array")
            if (input[pos] == ']') { pos++; return }
            expect(',')
        }
    }

    private fun parseString() {
        expect('"')
        while (pos < input.length && input[pos] != '"') {
            if (input[pos] == '\\') {
                pos++
                if (pos >= input.length) throw JsonValidationException("Unexpected end in string escape")
                when (input[pos]) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> pos++
                    'u' -> {
                        pos++
                        repeat(4) {
                            if (pos >= input.length || !input[pos].isLetterOrDigit())
                                throw JsonValidationException("Invalid unicode escape at $pos")
                            pos++
                        }
                    }
                    else -> throw JsonValidationException("Invalid escape '\\${input[pos]}' at $pos")
                }
            } else if (input[pos].code < 0x20) {
                throw JsonValidationException("Unescaped control character at $pos")
            } else {
                pos++
            }
        }
        if (pos >= input.length) throw JsonValidationException("Unterminated string")
        pos++
    }

    private fun parseNumber() {
        if (pos < input.length && input[pos] == '-') pos++
        if (pos >= input.length || !input[pos].isDigit())
            throw JsonValidationException("Invalid number at $pos")
        if (input[pos] == '0') { pos++ } else {
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        if (pos < input.length && input[pos] == '.') {
            pos++
            if (pos >= input.length || !input[pos].isDigit())
                throw JsonValidationException("Invalid number fraction at $pos")
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            if (pos >= input.length || !input[pos].isDigit())
                throw JsonValidationException("Invalid number exponent at $pos")
            while (pos < input.length && input[pos].isDigit()) pos++
        }
    }

    private fun parseLiteral(expected: String) {
        for (ch in expected) {
            if (pos >= input.length || input[pos] != ch)
                throw JsonValidationException("Expected '$expected' at $pos")
            pos++
        }
    }

    private fun expect(ch: Char) {
        skipWhitespace()
        if (pos >= input.length || input[pos] != ch)
            throw JsonValidationException("Expected '$ch' at $pos")
        pos++
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }
}
