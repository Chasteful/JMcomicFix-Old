package net.ccbluex.jmcomicfix.utils.tuples.immutable

import net.ccbluex.jmcomicfix.utils.tuples.Pair
import java.util.function.BiConsumer
import java.util.function.BiFunction

class ImmutablePair<A, B> internal constructor(override val first: A, override val second: B) : Pair<A, B>() {
    override fun <R> apply(func: BiFunction<in A, in B, out R>): R {
        return func.apply(first, second)
    }

    override fun use(func: BiConsumer<in A, in B>) {
        func.accept(first, second)
    }

    companion object {
        fun <A, B> of(a: A, b: B): ImmutablePair<A, B> {
            return ImmutablePair(a, b)
        }
    }
}
