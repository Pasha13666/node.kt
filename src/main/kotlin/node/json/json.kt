package node.json

/* Part of klaxon: https://github.com/cbeust/klaxon
 * License: Apache License version 2.0 (see /license-json.txt)
 * Changes:
 *   1) Removed unused functions and constructors.
 *   2) On `Type`, `TokenStatus`, `Lexer`, `Status`, `World` and `StateMachine`
 *      classes changed access type to `private`.
 *   3) Removed class `Parser`.
 *   4) Added functions `parseJson` (from class `Parser`), `Any?.toJson`,
 *      `String.asJson` and `File.asJson`.
 *   5) Some small changes.
 */

import java.io.File
import java.io.Reader
import java.math.BigInteger
import java.util.*
import java.util.regex.Pattern

private enum class Type {
    VALUE,
    LEFT_BRACE,
    RIGHT_BRACE,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    COMMA,
    COLON,
    EOF
}

private data class Token(val tokenType: Type, val value: Any?)

private class Lexer(reader: Reader) {
    val EOF = Token(Type.EOF, null)
    var index = 0

    val NUMERIC = Pattern.compile("[-]?[0-9]+")!!
    val DOUBLE = Pattern.compile(NUMERIC.toString() + "((\\.[0-9]+)?([eE][-+]?[0-9]+)?)")!!

    fun isSpace(c: Char): Boolean {
        return c == ' ' || c == '\r' || c == '\n' || c == '\t'
    }

    private val reader = reader.buffered()
    private var next: Char? = null

    private fun nextChar(): Char {
        if (isDone()) throw IllegalStateException("Cannot get next char: EOF reached")
        val c = next!!
        next = null
        return c
    }

    private fun peekChar() : Char {
        if (isDone()) throw IllegalStateException("Cannot peek next char: EOF reached")
        return next!!
    }

    private fun isDone() : Boolean {
        if (next != null) {
            return false
        }
        index++
        val read = reader.read()
        if (read == -1) return true
        next = read.toChar()
        return false
    }

    val BOOLEAN_LETTERS = "falsetrue".toSet()
    private fun isBooleanLetter(c: Char) : Boolean {
        return BOOLEAN_LETTERS.contains(Character.toLowerCase(c))
    }

    val NULL_LETTERS = "null".toSet()

    fun isValueLetter(c: Char) : Boolean {
        return c == '-' || c == '+' || c == '.' || c.isDigit() || isBooleanLetter(c)
                || c in NULL_LETTERS
    }

    fun nextToken() : Token {

        if (isDone()) {
            return EOF
        }

        val tokenType: Type
        var c = nextChar()
        val currentValue = StringBuilder()
        var jsonValue: Any? = null

        while (! isDone() && isSpace(c)) {
            c = nextChar()
        }

        if ('"' == c) {
            tokenType = Type.VALUE
            loop@ do {
                if (isDone())
                    throw RuntimeException("Unterminated string")

                c = nextChar()
                when (c) {
                    '\\' -> {
                        if (isDone())
                            throw RuntimeException("Unterminated string")

                        c = nextChar()
                        when (c) {
                            '\\' -> currentValue.append("\\")
                            '/' -> currentValue.append("/")
                            'b' -> currentValue.append("\b")
                            'f' -> currentValue.append("\u000c")
                            'n' -> currentValue.append("\n")
                            'r' -> currentValue.append("\r")
                            't' -> currentValue.append("\t")
                            'u' -> currentValue.append(
                                    StringBuilder(4)
                                            .append(nextChar())
                                            .append(nextChar())
                                            .append(nextChar())
                                            .append(nextChar())
                                            .toString()
                                            .toInt(16)
                                            .toChar())
                            else -> currentValue.append(c)
                        }
                    }
                    '"' -> break@loop
                    else -> currentValue.append(c)
                }
            } while (true)

            jsonValue = currentValue.toString()
        } else if ('{' == c) {
            tokenType = Type.LEFT_BRACE
        } else if ('}' == c) {
            tokenType = Type.RIGHT_BRACE
        } else if ('[' == c) {
            tokenType = Type.LEFT_BRACKET
        } else if (']' == c) {
            tokenType = Type.RIGHT_BRACKET
        } else if (':' == c) {
            tokenType = Type.COLON
        } else if (',' == c) {
            tokenType = Type.COMMA
        } else if (! isDone()) {
            while (isValueLetter(c)) {
                currentValue.append(c)
                if (! isValueLetter(peekChar())) {
                    break
                } else {
                    c = nextChar()
                }
            }
            val v = currentValue.toString()
            if (NUMERIC.matcher(v).matches()) {
                jsonValue = v.toIntOrNull()?: v.toLongOrNull()?: BigInteger(v)
            } else if (DOUBLE.matcher(v).matches()) {
                jsonValue = java.lang.Double.parseDouble(v)
            } else if ("true" == v.toLowerCase()) {
                jsonValue = true
            } else if ("false" == v.toLowerCase()) {
                jsonValue = false
            } else if (v == "null") {
                jsonValue = null
            } else {
                throw RuntimeException("Unexpected character at position $index: '$c (${c.toInt()})'")
            }

            tokenType = Type.VALUE
        } else {
            tokenType = Type.EOF
        }

        return Token(tokenType, jsonValue)
    }
}

private enum class Status {
    INIT,
    IN_FINISHED_VALUE,
    IN_OBJECT,
    IN_ARRAY,
    PASSED_PAIR_KEY,
}

private class World(var status : Status) {
    private val statusStack = LinkedList<Status>()
    private val valueStack = LinkedList<Any>()
    var result : Any? = null
    var parent = mutableMapOf<String, Any?>()

    fun pushAndSet(status: Status, value: Any) : World {
        pushStatus(status)
        pushValue(value)
        this.status = status
        return this
    }

    fun pushStatus(status: Status) : World {
        statusStack.addFirst(status)
        return this
    }

    fun pushValue(value: Any) : World {
        valueStack.addFirst(value)
        return this
    }

    fun popValue() = valueStack.removeFirst()!!

    fun popStatus() = statusStack.removeFirst()!!

    @Suppress("UNCHECKED_CAST")
    fun getFirstObject() = valueStack.first as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getFirstArray() = valueStack.first as MutableList<Any?>

    fun peekStatus() = statusStack[0]

    fun hasValues() = valueStack.size > 1
}

private object StateMachine {
    fun next(world: World, token: Token): World =
            when (world.status) {
                Status.INIT -> when (token.tokenType) {
                    Type.VALUE -> world.pushAndSet(Status.IN_FINISHED_VALUE, token.value!!)
                    Type.LEFT_BRACE -> world.pushAndSet(Status.IN_OBJECT, mutableMapOf<String, Any?>())
                    Type.LEFT_BRACKET -> world.pushAndSet(Status.IN_ARRAY, mutableListOf<Any>())
                    else -> throw RuntimeException("No state found: ${world.status} $token")
                }
                Status.IN_FINISHED_VALUE -> if (token.tokenType == Type.EOF) {
                    world.result = world.popValue()
                    world
                } else throw RuntimeException("No state found: ${world.status} $token")
                Status.IN_OBJECT -> when (token.tokenType){
                    Type.VALUE -> world.pushAndSet(Status.PASSED_PAIR_KEY, token.value!!)
                    Type.RIGHT_BRACE -> {
                        if (world.hasValues()) {
                            world.popStatus()
                            world.popValue()
                            world.status = world.peekStatus()
                        } else {
                            world.status = Status.IN_FINISHED_VALUE
                        }
                        world
                    }
                    Type.COMMA -> world
                    else -> throw RuntimeException("No state found: ${world.status} $token")
                }
                Status.IN_ARRAY -> when (token.tokenType){
                    Type.VALUE -> {
                        world.getFirstArray().add(token.value)
                        world
                    }
                    Type.LEFT_BRACE -> mutableMapOf<String, Any?>().let {
                        world.getFirstArray().add(it)
                        world.pushAndSet(Status.IN_OBJECT, it)
                    }
                    Type.LEFT_BRACKET -> mutableListOf<Any>().let {
                        world.getFirstArray().add(it)
                        world.pushAndSet(Status.IN_ARRAY, it)
                    }
                    Type.RIGHT_BRACKET -> {
                        if (world.hasValues()) {
                            world.popStatus()
                            world.popValue()
                            world.status = world.peekStatus()
                        } else {
                            world.status = Status.IN_FINISHED_VALUE
                        }
                        world
                    }
                    Type.COMMA -> world
                    else -> throw RuntimeException("No state found: ${world.status} $token")
                }
                Status.PASSED_PAIR_KEY -> when (token.tokenType){
                    Type.VALUE -> {
                        world.popStatus()
                        val key = world.popValue() as String
                        world.parent = world.getFirstObject()
                        world.parent.put(key, token.value)
                        world.status = world.peekStatus()
                        world
                    }
                    Type.LEFT_BRACE -> {
                        world.popStatus()
                        val key = world.popValue() as String
                        world.parent = world.getFirstObject()
                        val newObject = mutableMapOf<String, Any?>()
                        world.parent.put(key, newObject)
                        world.pushAndSet(Status.IN_OBJECT, newObject)
                    }
                    Type.LEFT_BRACKET -> {
                        world.popStatus()
                        val key = world.popValue() as String
                        world.parent = world.getFirstObject()
                        val newArray = mutableListOf<Any>()
                        world.parent.put(key, newArray)
                        world.pushAndSet(Status.IN_ARRAY, newArray)
                    }
                    Type.COLON -> world
                    else -> throw RuntimeException("No state found: ${world.status} $token")
                }
                else -> throw RuntimeException("No state found: ${world.status} $token")
            }
}

fun parseJson(reader: Reader): Any? {
    val lexer = Lexer(reader)

    var world = World(Status.INIT)
    do {
        val token = lexer.nextToken()
        world = StateMachine.next(world, token)
    } while (token.tokenType != Type.EOF)

    return world.result
}

private fun renderString(s: String): String {
    val sb = StringBuilder()
    sb.append("\"")

    s.forEach {
        when (it) {
            '"' -> sb.append("\\").append(it)
            '\\' -> sb.append(it).append(it)
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000c' -> sb.append("\\f")
            else -> {
                if(it in ('\u007F'..'\u009F') + ('\u0000'..'\u001F') + ('\u2000'..'\u20FF')){
                    sb.append("\\u")
                    sb.append(Integer.toHexString(it.toInt()).padStart(4, '0'))
                } else sb.append(it)
            }
        }
    }

    sb.append("\"")
    return sb.toString()
}

fun Any?.toJson(): String = when (this){
    is Int, is Long, is BigInteger, is Float, is Double, is Boolean, null -> this.toString()
    is String -> renderString(this)
    is Map<*, *> -> "{" + this.map { it.key.toJson() + ":" + it.value.toJson() }.joinToString(",") + "}"
    is List<*> -> "[" + this.map(Any?::toJson).joinToString(",") + "]"
    is Array<*> -> "[" + this.map(Any?::toJson).joinToString(",") + "]"
    else -> throw RuntimeException("Cant convert ${this::class.java.simpleName} object to json!")
}

fun String.asJson() = parseJson(this.reader())
fun File.asJson() = parseJson(this.reader())
