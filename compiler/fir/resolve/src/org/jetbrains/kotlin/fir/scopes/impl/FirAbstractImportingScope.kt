/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.declarations.expandedConeType
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.TowerScopeLevel
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.*
import org.jetbrains.kotlin.fir.symbols.CallableId
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.cast

abstract class FirAbstractImportingScope(
    session: FirSession,
    protected val scopeSession: ScopeSession,
    lookupInFir: Boolean
) : FirAbstractProviderBasedScope(session, lookupInFir) {

    // TODO: Rewrite somehow?
    private fun getStaticsScope(classId: ClassId): FirScope? {
        val symbol = provider.getClassLikeSymbolByFqName(classId) ?: return null
        if (symbol is FirTypeAliasSymbol) {
            val expansionSymbol = symbol.fir.expandedConeType?.lookupTag?.toSymbol(session)
            if (expansionSymbol != null) {
                return getStaticsScope(expansionSymbol.classId)
            }
        } else {
            return (symbol as FirClassSymbol<*>).fir.unsubstitutedScope(session, scopeSession)
        }

        return null
    }

    protected fun <T : FirCallableSymbol<*>> processCallables(
        import: FirResolvedImport,
        name: Name,
        token: TowerScopeLevel.Token<T>,
        processor: ScopeProcessor<FirCallableSymbol<*>>
    ) {
        val callableId = CallableId(import.packageFqName, import.relativeClassName, name)

        val classId = import.resolvedClassId
        if (classId != null) {
            val scope = getStaticsScope(classId) ?: return

            when (token) {
                TowerScopeLevel.Token.Functions -> scope.processFunctionsByName(
                    callableId.callableName,
                    processor.cast()
                )
                TowerScopeLevel.Token.Properties -> scope.processPropertiesByName(
                    callableId.callableName,
                    processor.cast()
                )
            }
        } else if (name.isSpecial || name.identifier.isNotEmpty()) {
            val symbols = provider.getTopLevelCallableSymbols(callableId.packageName, callableId.callableName)
            if (symbols.isEmpty()) {
                return
            }

            for (symbol in symbols) {
                processor.noSubstitution(symbol)
            }
        }

    }

    abstract fun <T : FirCallableSymbol<*>> processCallables(
        name: Name,
        token: TowerScopeLevel.Token<T>,
        processor: ScopeProcessor<FirCallableSymbol<*>>
    )

    final override fun processFunctionsByName(name: Name, processor: ScopeProcessor<FirFunctionSymbol<*>>) {
        return processCallables(
            name,
            TowerScopeLevel.Token.Functions
        ) {
            if (it.symbol is FirFunctionSymbol<*>) {
                @Suppress("UNCHECKED_CAST")
                processor(it as ScopeElement<FirFunctionSymbol<*>>)
            }
        }
    }

    final override fun processPropertiesByName(name: Name, processor: ScopeProcessor<FirVariableSymbol<*>>) {
        return processCallables(
            name,
            TowerScopeLevel.Token.Properties
        ) {
            if (it.symbol is FirVariableSymbol<*>) {
                @Suppress("UNCHECKED_CAST")
                processor(it as ScopeElement<FirVariableSymbol<*>>)
            }
        }
    }

}
