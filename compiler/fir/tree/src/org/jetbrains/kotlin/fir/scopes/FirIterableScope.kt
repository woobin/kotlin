/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes

import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.Name

abstract class FirIterableScope : FirScope() {
    abstract val scopes: Iterable<FirScope>

    override fun processClassifiersByName(
        name: Name,
        processor: ScopeProcessor<FirClassifierSymbol<*>>
    ) {
        for (scope in scopes) {
            scope.processClassifiersByName(name, processor)
        }
    }

    private inline fun <T> processComposite(
        process: FirScope.(Name, (T) -> Unit) -> Unit,
        name: Name,
        noinline processor: (T) -> Unit
    ) {
        val unique = mutableSetOf<T>()
        for (scope in scopes) {
            scope.process(name) {
                if (unique.add(it)) {
                    processor(it)
                }
            }
        }
    }

    override fun processFunctionsByName(name: Name, processor: ScopeProcessor<FirFunctionSymbol<*>>) {
        return processComposite(FirScope::processFunctionsByName, name, processor)
    }

    override fun processPropertiesByName(name: Name, processor: ScopeProcessor<FirVariableSymbol<*>>) {
        return processComposite(FirScope::processPropertiesByName, name, processor)
    }
}
