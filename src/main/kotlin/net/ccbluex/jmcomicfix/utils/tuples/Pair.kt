package net.ccbluex.jmcomicfix.utils.tuples

import net.ccbluex.jmcomicfix.utils.tuples.immutable.ImmutablePair
import java.io.Serializable
import java.util.*
import java.util.function.BiConsumer
import java.util.function.BiFunction

@Suppress("SerialVersionUIDInSerializableClass")
abstract class Pair<A, B> : Serializable {
    abstract val first: A
    abstract val second: B

    abstract fun <R> apply(func: BiFunction<in A, in B, out R>): R

    abstract fun use(func: BiConsumer<in A, in B>)

    override fun hashCode(): Int {
        return Objects.hash(first, second)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pair<*, *>) return false
        return first == other.first && second == other.second
    }

    companion object {
        fun <A, B> of(a: A, b: B): Pair<A, B> {
            return ImmutablePair.of(a, b)
        }

        fun <A> of(a: A): Pair<A, A> {
            return ImmutablePair.of(a, a)
        }
    }
}
