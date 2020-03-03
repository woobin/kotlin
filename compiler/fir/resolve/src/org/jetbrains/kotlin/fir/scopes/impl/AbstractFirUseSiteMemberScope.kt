/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.FirSimpleFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirValueParameterBuilder
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.scopes.FirOverrideChecker
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ScopeElement
import org.jetbrains.kotlin.fir.scopes.ScopeProcessor
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.Name

abstract class AbstractFirUseSiteMemberScope(
    session: FirSession,
    overrideChecker: FirOverrideChecker,
    protected val superTypesScope: FirScope,
    protected val declaredMemberScope: FirScope
) : AbstractFirOverrideScope(session, overrideChecker) {

    private val functions = hashMapOf<Name, Collection<ScopeElement<FirFunctionSymbol<*>>>>()

    override fun processFunctionsByName(name: Name, processor: ScopeProcessor<FirFunctionSymbol<*>>) {
        functions.getOrPut(name) {
            doProcessFunctions(name)
        }.forEach {
            processor(it)
        }
    }

    private fun doProcessFunctions(
        name: Name
    ): Collection<ScopeElement<FirFunctionSymbol<*>>> = mutableListOf<ScopeElement<FirFunctionSymbol<*>>>().apply {
        val overrideCandidates = mutableSetOf<ScopeElement<FirFunctionSymbol<*>>>()
        declaredMemberScope.processFunctionsByName(name) {
            val symbol = processInheritedDefaultParameters(it)
            overrideCandidates += symbol
            add(symbol)
        }

        superTypesScope.processFunctionsByName(name) {
            if (it.symbol !is FirConstructorSymbol) {
                val overriddenBy = it.getOverridden(overrideCandidates)
                if (overriddenBy == null) {
                    add(it)
                }
            }
        }
    }

    private fun processInheritedDefaultParameters(element: ScopeElement<FirFunctionSymbol<*>>): ScopeElement<FirFunctionSymbol<*>> {
        val symbol = element.symbol
        val firSimpleFunction = symbol.fir as? FirSimpleFunction ?: return element
        if (firSimpleFunction.valueParameters.isEmpty() || firSimpleFunction.valueParameters.any { it.defaultValue != null }) return element

        var foundFir: FirFunction<*>? = null
        superTypesScope.processFunctionsByName(symbol.callableId.callableName) { superElement ->
            val superFunctionFir = superElement.symbol.fir
            if (foundFir == null &&
                superFunctionFir is FirSimpleFunction &&
                overrideChecker.isOverriddenFunction(
                    firSimpleFunction, element.substitutorOrEmpty,
                    superFunctionFir, superElement.substitutorOrEmpty
                ) && superFunctionFir.valueParameters.any { parameter -> parameter.defaultValue != null }
            ) {
                foundFir = superFunctionFir
            }
        }

        if (foundFir == null) return element

        val newSymbol = FirNamedFunctionSymbol(symbol.callableId, false, null)

        createFunctionCopy(firSimpleFunction, newSymbol).apply {
            resolvePhase = firSimpleFunction.resolvePhase
            typeParameters += firSimpleFunction.typeParameters
            valueParameters += firSimpleFunction.valueParameters.zip(foundFir.valueParameters)
                .map { (overrideParameter, overriddenParameter) ->
                    if (overriddenParameter.defaultValue != null)
                        createValueParameterCopy(overrideParameter, overriddenParameter.defaultValue).apply {
                            annotations += overrideParameter.annotations
                        }.build()
                    else
                        overrideParameter
                }
        }.build()

        return ScopeElement(newSymbol, element.substitutor)
    }

    protected open fun createFunctionCopy(firSimpleFunction: FirSimpleFunction, newSymbol: FirNamedFunctionSymbol): FirSimpleFunctionBuilder =
        FirSimpleFunctionBuilder().apply {
            source = firSimpleFunction.source
            session = firSimpleFunction.session
            returnTypeRef = firSimpleFunction.returnTypeRef
            receiverTypeRef = firSimpleFunction.receiverTypeRef
            name = firSimpleFunction.name
            status = firSimpleFunction.status
            symbol = newSymbol
        }

    protected open fun createValueParameterCopy(parameter: FirValueParameter, newDefaultValue: FirExpression?): FirValueParameterBuilder =
        FirValueParameterBuilder().apply {
            source = parameter.source
            session = parameter.session
            returnTypeRef = parameter.returnTypeRef
            name = parameter.name
            symbol = FirVariableSymbol(parameter.symbol.callableId)
            defaultValue = newDefaultValue
            isCrossinline = parameter.isCrossinline
            isNoinline = parameter.isNoinline
            isVararg = parameter.isVararg
        }


    override fun processClassifiersByName(
        name: Name,
        processor: ScopeProcessor<FirClassifierSymbol<*>>
    ) {
        declaredMemberScope.processClassifiersByName(name, processor)
        superTypesScope.processClassifiersByName(name, processor)
    }
}
