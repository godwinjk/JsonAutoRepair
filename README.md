# JsonAutoRepair

A zero-dependency Kotlin/JVM library that automatically repairs malformed JSON strings.

Feed it broken JSON — missing braces, trailing commas, single quotes, unquoted keys, comments, multiline issues — and get valid JSON back.

## Installation

Add the JitPack repository and dependency:

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.godwin:json-auto-repair:v1.0.0")
}
```

### Gradle (Groovy)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.godwin:json-auto-repair:v1.0.0'
}
```

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.godwin</groupId>
    <artifactId>json-auto-repair</artifactId>
    <version>v1.0.0</version>
</dependency>
```

## Usage

```kotlin
import com.godwin.jsonautorepair.JsonAutoRepair

val repairer = JsonAutoRepair()

// Basic repair
val result = repairer.repair("""{"name": "Alice" "age": 30}""")
println(result.output)      // {"name": "Alice", "age": 30}
println(result.wasValid)     // false (input was invalid)
println(result.iterations)   // 1

// Already valid JSON passes through untouched
val valid = repairer.repair("""{"name": "Alice", "age": 30}""")
println(valid.wasValid)      // true
println(valid.iterations)    // 0
```

## What It Fixes

| Issue | Input | Output |
|---|---|---|
| Missing closing braces | `{"a": 1` | `{"a": 1}` |
| Missing closing brackets | `[1, 2, 3` | `[1, 2, 3]` |
| Trailing commas | `{"a": 1,}` | `{"a": 1}` |
| Missing commas | `{"a": 1 "b": 2}` | `{"a": 1, "b": 2}` |
| Single quotes | `{'name': 'Alice'}` | `{"name": "Alice"}` |
| Unquoted keys | `{name: "Alice"}` | `{"name": "Alice"}` |
| Missing colons | `{"name" "Alice"}` | `{"name": "Alice"}` |
| Boolean variants | `True`, `False` | `true`, `false` |
| Null variants | `None`, `undefined`, `NULL` | `null` |
| Comments | `{/* comment */ "a": 1}` | `{"a": 1}` |
| Multiline strings | Strings broken across lines | Properly terminated strings |
| Deeply nested errors | Multiple combined issues | Valid nested JSON |

## Complex Example

```kotlin
val repairer = JsonAutoRepair()

val broken = """
{
    // user config
    'name': 'Alice'
    age: 30,
    active: True
    tags: ['admin', "user" 'editor',],
    metadata: {
        created: "2024-01-15"
        updated: None
        verified: False,
    },
    scores: [95 87 72 100,]
}
"""

val result = repairer.repair(broken)
println(result.output)
// {"name": "Alice", "age": 30, "active": true, "tags": ["admin", "user", "editor"],
//  "metadata": {"created": "2024-01-15", "updated": null, "verified": false},
//  "scores": [95, 87, 72, 100]}
```

## Configuration

```kotlin
// Custom max iterations (default is 10)
val repairer = JsonAutoRepair(maxIterations = 5)
```

## Validation Only

The library also exposes its built-in JSON validator:

```kotlin
import com.godwin.jsonautorepair.JsonValidator

try {
    JsonValidator("""{"valid": true}""").validate()
    println("Valid JSON")
} catch (e: JsonValidator.JsonValidationException) {
    println("Invalid: ${e.message}")
}
```

## Building

```bash
# Run tests
./gradlew test

# Build JAR
./gradlew jar

# Build JAR with sources and javadoc
./gradlew build

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

The JAR is output to `build/libs/json-auto-repair-1.0.0.jar`.

## License

MIT
