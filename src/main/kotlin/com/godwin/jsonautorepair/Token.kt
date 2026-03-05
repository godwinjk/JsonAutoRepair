package com.godwin.jsonautorepair

enum class TokenType {
    LEFT_BRACE,      // {
    RIGHT_BRACE,     // }
    LEFT_BRACKET,    // [
    RIGHT_BRACKET,   // ]
    COLON,           // :
    COMMA,           // ,
    STRING,          // "..."
    NUMBER,          // 123, -1.5, 1e10
    TRUE,            // true
    FALSE,           // false
    NULL,            // null
    UNKNOWN,         // unrecognized token
    EOF
}

data class Token(
    val type: TokenType,
    val value: String,
    val position: Int
)
