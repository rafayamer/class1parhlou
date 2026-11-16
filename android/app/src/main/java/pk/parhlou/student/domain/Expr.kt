package pk.parhlou.student.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small, safe reader for the expressions in Ustaad's graphs ("2*x - 3", "x^2 - 4", "sqrt(x)", "3x + 1", "2(x+1)").
 * Only x, numbers, pi, + − * / ^, brackets and a few functions are understood; anything else fails to parse (null).
 * Values outside a function's domain are NaN, so the graph leaves a gap there.
 */
object Expr {
    fun parse(text: String): ((Double) -> Double)? = runCatching {
        val p = Parser(normalise(text))
        val f = p.expr()
        if (!p.done()) null else f
    }.getOrNull()

    private fun normalise(s: String) = s.replace(" ", "").replace("×", "*").replace("÷", "/").replace("−", "-")
        .replace("√", "sqrt").replace("π", "pi").lowercase()

    private class Parser(val s: String) {
        var i = 0
        fun done() = i == s.length
        private fun peek() = s.getOrNull(i)
        private fun eat(c: Char): Boolean = if (peek() == c) { i++; true } else false

        fun expr(): (Double) -> Double {
            var left = term()
            while (true) {
                left = when {
                    eat('+') -> { val a = left; val b = term(); { x -> a(x) + b(x) } }
                    eat('-') -> { val a = left; val b = term(); { x -> a(x) - b(x) } }
                    else -> return left
                }
            }
        }

        private fun term(): (Double) -> Double {
            var left = unary()
            while (true) {
                left = when {
                    eat('*') -> { val a = left; val b = unary(); { x -> a(x) * b(x) } }
                    eat('/') -> { val a = left; val b = unary(); { x -> b(x).let { d -> if (d == 0.0) Double.NaN else a(x) / d } } }
                    // Implicit multiplication: "3x", "2(x+1)", "x sin(x)".
                    peek() != null && (peek()!!.isLetter() || peek() == '(') -> { val a = left; val b = unary(); { x -> a(x) * b(x) } }
                    else -> return left
                }
            }
        }

        private fun unary(): (Double) -> Double = when {
            eat('-') -> { val a = unary(); { x -> -a(x) } }
            eat('+') -> unary()
            else -> power()
        }

        private fun power(): (Double) -> Double {
            val base = atom()
            if (!eat('^')) return base
            val e = unary()
            return { x -> base(x).pow(e(x)) }
        }

        private fun atom(): (Double) -> Double {
            val c = peek() ?: error("end")
            if (eat('(')) { val e = expr(); check(eat(')')); return e }
            if (c.isDigit() || c == '.') {
                val start = i
                while (peek()?.let { it.isDigit() || it == '.' } == true) i++
                val v = s.substring(start, i).toDouble()
                return { v }
            }
            if (c.isLetter()) {
                val start = i
                while (peek()?.isLetter() == true) i++
                val word = s.substring(start, i)
                if (word == "x") return { x -> x }
                if (word == "pi") return { Math.PI }
                // "xsin" etc. is not supported; functions need brackets.
                val fn: (Double) -> Double = when (word) {
                    "sqrt" -> { v -> if (v < 0) Double.NaN else sqrt(v) }
                    "abs" -> { v -> abs(v) }
                    "sin" -> { v -> sin(v) }
                    "cos" -> { v -> cos(v) }
                    "tan" -> { v -> tan(v) }
                    "log" -> { v -> if (v <= 0) Double.NaN else log10(v) }
                    "ln" -> { v -> if (v <= 0) Double.NaN else ln(v) }
                    "exp" -> { v -> exp(v) }
                    else -> error("unknown $word")
                }
                check(eat('('))
                val arg = expr()
                check(eat(')'))
                return { x -> fn(arg(x)) }
            }
            error("unexpected $c")
        }
    }
}
