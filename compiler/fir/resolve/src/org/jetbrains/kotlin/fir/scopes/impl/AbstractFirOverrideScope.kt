/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.scopes.FirOverrideChecker
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ScopeElement
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol

abstract class AbstractFirOverrideScope(val session: FirSession, protected val overrideChecker: FirOverrideChecker) : FirScope() {
    //base symbol as key, overridden as value
    val overrideByBase = mutableMapOf<FirCallableSymbol<*>, ScopeElement<FirCallableSymbol<*>>?>()

    protected fun similarFunctionsOrBothProperties(
        overrideCandidate: FirCallableMemberDeclaration<*>,
        overrideSubstitutor: ConeSubstitutor,
        baseDeclaration: FirCallableMemberDeclaration<*>,
        baseSubstitutor: ConeSubstitutor
    ): Boolean {
        return when (overrideCandidate) {
            is FirSimpleFunction -> when (baseDeclaration) {
                is FirSimpleFunction ->
                    overrideChecker.isOverriddenFunction(
                        overrideCandidate, overrideSubstitutor,
                        baseDeclaration, baseSubstitutor,
                    )
                is FirProperty ->
                    overrideChecker.isOverriddenProperty(
                        overrideCandidate, overrideSubstitutor,
                        baseDeclaration, baseSubstitutor,
                    )
                else -> false
            }
            is FirConstructor -> false
            is FirProperty -> baseDeclaration is FirProperty && overrideChecker.isOverriddenProperty(
                overrideCandidate, overrideSubstitutor,
                baseDeclaration, baseSubstitutor,
            )
            is FirField -> false
            else -> error("Unknown fir callable type: $overrideCandidate, $baseDeclaration")
        }
    }

    // Receiver is super-type function here
    protected open fun ScopeElement<FirCallableSymbol<*>>.getOverridden(
        overrideCandidates: Set<ScopeElement<FirCallableSymbol<*>>>
    ): ScopeElement<FirCallableSymbol<*>>? {
        if (overrideByBase.containsKey(symbol)) return overrideByBase[symbol]

        val baseDeclaration = (symbol as AbstractFirBasedSymbol<*>).fir as FirCallableMemberDeclaration<*>
        val baseSubstitutor = substitutorOrEmpty
        val override = overrideCandidates.firstOrNull {
            val overrideCandidate = (it.symbol as AbstractFirBasedSymbol<*>).fir as FirCallableMemberDeclaration<*>
            baseDeclaration.modality != Modality.FINAL &&
                    similarFunctionsOrBothProperties(
                        overrideCandidate,
                        it.substitutorOrEmpty,
                        baseDeclaration,
                        baseSubstitutor
                    )
        } // TODO: two or more overrides for one fun?
        overrideByBase[symbol] = override
        return override
    }

}
