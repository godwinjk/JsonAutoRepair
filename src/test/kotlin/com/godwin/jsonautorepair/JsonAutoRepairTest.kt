package com.godwin.jsonautorepair

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonAutoRepairTest {

    private val repairer = JsonAutoRepair()

    private fun repairAndValidate(input: String): String {
        val result = repairer.repair(input)
        try {
            JsonValidator(result.output).validate()
        } catch (e: Exception) {
            throw AssertionError("Repair produced invalid JSON: '${result.output}' from input: '$input'", e)
        }
        return result.output
    }

    private fun isValidJsonStr(s: String): Boolean {
        return try { JsonValidator(s).validate(); true } catch (_: Exception) { false }
    }

    @Nested
    inner class ValidJson {
        @Test fun `valid object passes through`() {
            val input = """{"name": "Alice", "age": 30}"""
            val result = repairer.repair(input)
            assertEquals(input, result.output); assertTrue(result.wasValid); assertEquals(0, result.iterations)
        }
        @Test fun `valid array passes through`() {
            val result = repairer.repair("""[1, 2, 3]"""); assertEquals("""[1, 2, 3]""", result.output); assertTrue(result.wasValid)
        }
        @Test fun `valid nested structure passes through`() {
            val result = repairer.repair("""{"a": [1, {"b": true}], "c": null}"""); assertTrue(result.wasValid)
        }
        @Test fun `valid string value`() { assertTrue(repairer.repair(""""hello"""").wasValid) }
        @Test fun `valid number`() { assertTrue(repairer.repair("42").wasValid) }
        @Test fun `valid boolean and null`() {
            assertTrue(repairer.repair("true").wasValid); assertTrue(repairer.repair("false").wasValid); assertTrue(repairer.repair("null").wasValid)
        }
    }

    @Nested
    inner class MissingClosing {
        @Test fun `missing closing brace`() = assertEquals("""{"a": 1}""", repairAndValidate("""{"a": 1"""))
        @Test fun `missing closing bracket`() = assertEquals("""[1, 2, 3]""", repairAndValidate("""[1, 2, 3"""))
        @Test fun `missing multiple closing braces`() = assertEquals("""{"a": {"b": 1}}""", repairAndValidate("""{"a": {"b": 1"""))
        @Test fun `missing closing bracket in nested`() = assertEquals("""{"a": [1, 2]}""", repairAndValidate("""{"a": [1, 2"""))
        @Test fun `deeply nested missing closers`() = assertEquals("""{"a": [{"b": [1, 2]}]}""", repairAndValidate("""{"a": [{"b": [1, 2"""))
    }

    @Nested
    inner class TrailingCommas {
        @Test fun `trailing comma in object`() = assertEquals("""{"a": 1, "b": 2}""", repairAndValidate("""{"a": 1, "b": 2,}"""))
        @Test fun `trailing comma in array`() = assertEquals("""[1, 2, 3]""", repairAndValidate("""[1, 2, 3,]"""))
        @Test fun `multiple trailing commas`() = assertEquals("""[1, 2]""", repairAndValidate("""[1, 2,,,]"""))
    }

    @Nested
    inner class MissingCommas {
        @Test fun `missing comma between object entries`() = assertEquals("""{"a": 1, "b": 2}""", repairAndValidate("""{"a": 1 "b": 2}"""))
        @Test fun `missing comma between array elements`() = assertEquals("""[1, 2, 3]""", repairAndValidate("""[1 2 3]"""))
        @Test fun `missing comma between nested objects`() = assertEquals("""[{"a": 1}, {"b": 2}]""", repairAndValidate("""[{"a": 1} {"b": 2}]"""))
    }

    @Nested
    inner class SingleQuotes {
        @Test fun `single quoted strings`() = assertEquals("""{"name": "Alice"}""", repairAndValidate("""{'name': 'Alice'}"""))
        @Test fun `mixed quotes`() = assertEquals("""{"name": "Alice", "age": 30}""", repairAndValidate("""{"name": 'Alice', 'age': 30}"""))
    }

    @Nested
    inner class UnquotedKeys {
        @Test fun `unquoted object keys`() = assertEquals("""{"name": "Alice"}""", repairAndValidate("""{name: "Alice"}"""))
        @Test fun `multiple unquoted keys`() = assertEquals("""{"name": "Alice", "age": 30}""", repairAndValidate("""{name: "Alice", age: 30}"""))
    }

    @Nested
    inner class MissingColons {
        @Test fun `missing colon between key and value`() = assertEquals("""{"name": "Alice"}""", repairAndValidate("""{"name" "Alice"}"""))
    }

    @Nested
    inner class BooleanNullVariants {
        @Test fun `capitalized True False`() = assertEquals("""{"a": true, "b": false}""", repairAndValidate("""{"a": True, "b": False}"""))
        @Test fun `None becomes null`() = assertEquals("""{"a": null}""", repairAndValidate("""{"a": None}"""))
        @Test fun `undefined becomes null`() = assertEquals("""{"a": null}""", repairAndValidate("""{"a": undefined}"""))
    }

    @Nested
    inner class EmptyInput {
        @Test fun `empty string`() = assertEquals("null", repairAndValidate(""))
        @Test fun `whitespace only`() = assertEquals("null", repairAndValidate("   "))
    }

    @Nested
    inner class Comments {
        @Test fun `single line comments`() = assertEquals("""{"a": 1}""", repairAndValidate("{\n// this is a comment\n\"a\": 1\n}"))
        @Test fun `block comments`() = assertEquals("""{"a": 1}""", repairAndValidate("""{/* comment */ "a": 1}"""))
    }

    @Nested
    inner class LeadingCommas {
        @Test fun `leading comma in array`() = assertEquals("""[1, 2]""", repairAndValidate("""[,1, 2]"""))
        @Test fun `leading comma in object`() = assertEquals("""{"a": 1}""", repairAndValidate("""{,"a": 1}"""))
    }

    @Nested
    inner class NumberEdgeCases {
        @Test fun `negative number`() = assertEquals("-42", repairAndValidate("-42"))
        @Test fun `decimal number`() = assertEquals("3.14", repairAndValidate("3.14"))
        @Test fun `scientific notation`() = assertEquals("1e10", repairAndValidate("1e10"))
        @Test fun `negative exponent`() = assertEquals("1.5e-3", repairAndValidate("1.5e-3"))
    }

    @Nested
    inner class StringEdgeCases {
        @Test fun `string with escaped quotes`() { assertTrue(repairer.repair("""{"msg": "He said \"hello\""}""").let { it.wasValid || isValidJsonStr(it.output) }) }
        @Test fun `string with unicode escape`() { assertTrue(repairer.repair("""{"emoji": "\u0041"}""").let { it.wasValid || isValidJsonStr(it.output) }) }
        @Test fun `string with newlines`() { assertTrue(repairAndValidate("""{"a": "line1\nline2"}""").contains("\\n")) }
        @Test fun `empty string value`() { val r = repairer.repair("""{"a": ""}"""); assertEquals("""{"a": ""}""", r.output); assertTrue(r.wasValid) }
    }

    @Nested
    inner class ComplexStructures {
        @Test fun `array of objects missing commas and braces`() = assertEquals("""[{"a": 1}, {"b": 2}]""", repairAndValidate("""[{"a": 1} {"b": 2}"""))
        @Test fun `nested arrays`() = assertEquals("""[[1, 2], [3, 4]]""", repairAndValidate("""[[1, 2], [3, 4]"""))
        @Test fun `object with array value missing bracket`() { assertTrue(isValidJsonStr(repairAndValidate("""{"items": [1, 2, 3}"""))) }
        @Test fun `empty object`() { val r = repairer.repair("{}"); assertEquals("{}", r.output); assertTrue(r.wasValid) }
        @Test fun `empty array`() { val r = repairer.repair("[]"); assertEquals("[]", r.output); assertTrue(r.wasValid) }
    }

    @Nested
    inner class IterationLimit {
        @Test fun `respects max iterations`() { assertTrue(JsonAutoRepair(maxIterations = 1).repair("""{"a": 1""").iterations <= 1) }
        @Test fun `already valid needs zero iterations`() = assertEquals(0, repairer.repair("""{"a": 1}""").iterations)
    }

    @Nested
    inner class WasValidFlag {
        @Test fun `valid input sets wasValid true`() { assertTrue(repairer.repair("""{"a": 1}""").wasValid) }
        @Test fun `invalid input sets wasValid false`() { assertTrue(!repairer.repair("""{"a": 1""").wasValid) }
    }

    @Nested
    inner class MixedIssues {
        @Test fun `trailing comma and missing brace`() = assertEquals("""{"a": 1, "b": 2}""", repairAndValidate("""{"a": 1, "b": 2,"""))
        @Test fun `single quotes unquoted keys trailing comma`() = assertEquals("""{"name": "Alice", "age": 30}""", repairAndValidate("""{name: 'Alice', age: 30,}"""))
        @Test fun `comments and trailing commas`() {
            val output = repairAndValidate("{\n// name\n\"name\": \"Bob\",\n// age\n\"age\": 25,\n}")
            assertTrue(isValidJsonStr(output)); assertTrue(output.contains("\"Bob\""))
        }
    }

    @Nested
    inner class AdditionalEdgeCases {
        @Test fun `only opening brace`() = assertEquals("{}", repairAndValidate("{"))
        @Test fun `only opening bracket`() = assertEquals("[]", repairAndValidate("["))
        @Test fun `extra closing brace ignored`() { assertTrue(isValidJsonStr(repairAndValidate("""{"a": 1}}"""))) }
        @Test fun `extra closing bracket ignored`() { assertTrue(isValidJsonStr(repairAndValidate("""[1, 2]]"""))) }
        @Test fun `bare string value`() = assertEquals(""""hello"""", repairAndValidate("hello"))
        @Test fun `bare number`() { val r = repairer.repair("42"); assertEquals("42", r.output); assertTrue(r.wasValid) }
        @Test fun `nested empty objects`() { assertTrue(repairer.repair("""{"a": {}, "b": []}""").wasValid) }
        @Test fun `string with embedded double quotes from single-quoted input`() { assertTrue(isValidJsonStr(repairAndValidate("""{'key': 'value with "quotes"'}"""))) }
        @Test fun `multiple consecutive commas in array`() = assertEquals("""[1, 2]""", repairAndValidate("""[1,,,,2]"""))
        @Test fun `object with only commas`() = assertEquals("""{}""", repairAndValidate("""{,,,}"""))
        @Test fun `array with only commas`() = assertEquals("""[]""", repairAndValidate("""[,,,]"""))
        @Test fun `unquoted value in object treated as string`() { val o = repairAndValidate("""{name: Alice}"""); assertTrue(isValidJsonStr(o)); assertTrue(o.contains("\"Alice\"")) }
        @Test fun `mixed array types`() { assertTrue(repairer.repair("""[1, "two", true, null, {"a": 1}]""").wasValid) }
        @Test fun `deeply nested missing all closers`() { assertTrue(isValidJsonStr(repairAndValidate("""{"a": {"b": {"c": [1, 2, {"d": 3"""))) }
        @Test fun `tab and newline in input`() { assertTrue(repairer.repair("{\n\t\"a\":\t1\n}").wasValid) }
        @Test fun `unicode in string`() { assertTrue(repairer.repair("""{"emoji": "\u0048\u0065\u006C\u006C\u006F"}""").wasValid) }
        @Test fun `negative decimal number`() { val r = repairer.repair("-3.14"); assertEquals("-3.14", r.output); assertTrue(r.wasValid) }
        @Test fun `zero`() { val r = repairer.repair("0"); assertEquals("0", r.output); assertTrue(r.wasValid) }
        @Test fun `object key without value and missing brace`() { assertTrue(isValidJsonStr(repairAndValidate("""{"key":"""))) }
        @Test fun `array with nested arrays missing brackets`() { assertTrue(isValidJsonStr(repairAndValidate("""[[1, 2, [3, 4"""))) }
        @Test fun `multiline string gets repaired`() { assertTrue(isValidJsonStr(repairAndValidate("{\n  \"key\": \"value\nwith newline\"\n}"))) }
        @Test fun `multiline object with missing quotes`() {
            val o = repairAndValidate("{\n  \"a\": \"hello\n  \"b\": 2\n}")
            assertTrue(isValidJsonStr(o)); assertTrue(o.contains("\"a\"")); assertTrue(o.contains("\"b\""))
        }
        @Test fun `multiline with single quoted strings`() { assertTrue(isValidJsonStr(repairAndValidate("{\n  'key': 'value\n  across lines'\n}"))) }
        @Test fun `array without quotes and having a newline`() { assertTrue(isValidJsonStr(repairAndValidate("{\"god\":\"name\n}"))) }
        @Test fun `random string`() = assertEquals(""""god"""", repairAndValidate("god"))
        @Test fun `random string with one curly bracket`() = assertEquals("""{"god": null}""", repairAndValidate("""{"god"}"""))
    }

    @Nested
    inner class ComplexMalformedJson {

        @Test
        fun `deeply nested nightmare with every issue combined`() {
            val input = """{
                // user profile
                'name': 'Alice'
                age: 30,
                active: True
                address: {
                    street: "123 Main St"
                    city: 'Springfield',
                    state: "IL"
                    zip: 62704,
                    /* coordinates */
                    coords: {
                        lat: 39.7817
                        lng: -89.6501
                    }
                },
                tags: ['admin', "user" 'editor',],
                metadata: {
                    created: "2024-01-15"
                    updated: None
                    deleted: undefined
                    verified: False,
                },
                scores: [95 87 72 100,],
                nested: [[1, 2, [3, 4 [5]]],
                preferences: {
                    theme: 'dark'
                    notifications: {
                        email: True
                        sms: False
                        push: None
                    }
                    language: "en"
                }
            """.trimIndent()
            val output = repairAndValidate(input)
            assertTrue(output.contains("\"name\""))
            assertTrue(output.contains("\"Alice\""))
            assertTrue(output.contains("\"age\""))
            assertTrue(output.contains("true"))
            assertTrue(output.contains("false"))
            assertTrue(output.contains("null"))
            assertTrue(output.contains("\"scores\""))
            assertTrue(output.contains("\"dark\""))
        }

        @Test
        fun `multiline with broken strings across lines and mixed issues`() {
            val input = "{\n" +
                "  'user': {\n" +
                "    \"name\": \"John\n" +
                "    'email': 'john@example.com\n" +
                "    age: 28\n" +
                "    bio: \"Loves coding\n" +
                "    and coffee\"\n" +
                "    active: True\n" +
                "  }\n" +
                "  'orders': [\n" +
                "    {id: 1 product: 'Widget' price: 9.99 qty: 3}\n" +
                "    {id: 2 product: 'Gadget' price: 19.99, qty: 1,}\n" +
                "    {id: 3, product: \"Doohickey\", price: 4.50 qty: 10\n" +
                "  ]\n" +
                "  total: 84.47\n" +
                "}"
            val output = repairAndValidate(input)
            assertTrue(output.contains("\"user\""))
            assertTrue(output.contains("\"orders\""))
            assertTrue(output.contains("\"Widget\""))
            assertTrue(output.contains("\"Gadget\""))
            assertTrue(output.contains("9.99"))
        }

        @Test
        fun `array of heterogeneous broken objects`() {
            val input = """
            [
                {name: "Alice" age: 30 hobbies: ['reading' 'coding']}
                {name: 'Bob', age: 25, hobbies: ["gaming", "cooking",],}
                {'name': "Charlie", age: 35 hobbies: ['hiking'
                {name: "Diana" age: 28, hobbies: []}
                {name: "Eve", 'age': 22 hobbies: ['music' "art" 'dance',]
            """.trimIndent()
            val output = repairAndValidate(input)
            assertTrue(output.contains("\"Alice\""))
            assertTrue(output.contains("\"Bob\""))
            assertTrue(output.contains("\"Charlie\""))
            assertTrue(output.contains("\"Diana\""))
            assertTrue(output.contains("\"Eve\""))
            assertTrue(output.contains("\"reading\""))
            assertTrue(output.contains("\"gaming\""))
        }

        @Test
        fun `config file style with comments and trailing commas everywhere`() {
            val input = """
            {
                // Database settings
                "database": {
                    "host": "localhost",
                    "port": 5432,
                    /* credentials */
                    "user": "admin",
                    "password": "secret123",
                    "options": {
                        "ssl": True,
                        "timeout": 30,
                        "retries": 3,
                        "pool": {
                            "min": 5,
                            "max": 20,
                            "idle": 10000,
                        },
                    },
                },
                // Cache settings
                "cache": {
                    "enabled": True,
                    "ttl": 3600,
                    "backend": "redis",
                    "nodes": [
                        "redis-1:6379",
                        "redis-2:6379",
                        "redis-3:6379",
                    ],
                },
                // Feature flags
                "features": {
                    "darkMode": True,
                    "betaAccess": False,
                    "maintenance": False,
                    "experimentalApi": None,
                },
            }
            """.trimIndent()
            val output = repairAndValidate(input)
            assertTrue(output.contains("\"database\""))
            assertTrue(output.contains("\"cache\""))
            assertTrue(output.contains("\"features\""))
            assertTrue(output.contains("\"redis-2:6379\""))
            assertTrue(output.contains("true"))
            assertTrue(output.contains("false"))
            assertTrue(output.contains("null"))
        }

        @Test
        fun `deeply nested arrays and objects 5 levels deep with missing closers`() {
            val input = """{
                "l1": {
                    "l2": {
                        "l3": {
                            "l4": {
                                "l5": [1, 2, {"key": "value"
                            }
                        }
                    }
                }
            """.trimIndent()
            val output = repairAndValidate(input)
            assertTrue(output.contains("\"l1\""))
            assertTrue(output.contains("\"l5\""))
            assertTrue(output.contains("\"value\""))
        }

        @Test
        fun `mixed quotes colons commas and brackets all broken at once`() {
            val input = """{
                'users' [
                    {'id' 1 'name' 'Alice' 'roles' ['admin' 'user'} 'active' True}
                    {"id" 2, "name": "Bob" "roles": ["user"] "active": False,}
                    {id 3 name 'Charlie' roles ['editor' "viewer",] active None
                ]
                'count' 3
            }""".trimIndent()
            val output = repairAndValidate(input)
            assertTrue(output.contains("\"users\""))
            assertTrue(output.contains("\"Alice\""))
            assertTrue(output.contains("\"Bob\""))
            assertTrue(output.contains("\"Charlie\""))
            assertTrue(output.contains("\"count\""))
        }
    }
}
