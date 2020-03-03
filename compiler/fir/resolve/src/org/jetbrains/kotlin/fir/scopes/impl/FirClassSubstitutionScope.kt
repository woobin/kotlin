/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.compose
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ScopeElement
import org.jetbrains.kotlin.fir.scopes.ScopeProcessor
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.name.Name

class FirClassSubstitutionScope(
    private val session: FirSession,
    private val useSiteMemberScope: FirScope,
    scopeSession: ScopeSession,
    private val substitutor: ConeSubstitutor,
    private val skipPrivateMembers: Boolean
) : FirScope() {

    constructor(
        session: FirSession, useSiteMemberScope: FirScope, scopeSession: ScopeSession,
        substitution: Map<FirTypeParameterSymbol, ConeKotlinType>,
        skipPrivateMembers: Boolean
    ) : this(session, useSiteMemberScope, scopeSession, substitutorByMap(substitution), skipPrivateMembers)

    override fun processFunctionsByName(name: Name, processor: ScopeProcessor<FirFunctionSymbol<*>>) {
        useSiteMemberScope.processFunctionsByName(name) process@{ original ->
            processor(ScopeElement(original.symbol, original.substitutorOrEmpty.compose(substitutor)))
        }


        return super.processFunctionsByName(name, processor)
    }

    override fun processPropertiesByName(name: Name, processor: ScopeProcessor<FirVariableSymbol<*>>) {
        return useSiteMemberScope.processPropertiesByName(name) process@{ original ->
            processor(ScopeElement(original.symbol, original.substitutorOrEmpty.compose(substitutor)))
        }
    }

    override fun processClassifiersByName(name: Name, processor: ScopeProcessor<FirClassifierSymbol<*>>) {
        useSiteMemberScope.processClassifiersByName(name, processor)
    }

    companion object {
        private fun createFakeOverrideFunction(
            fakeOverrideSymbol: FirFunctionSymbol<FirSimpleFunction>,
            session: FirSession,
            baseFunction: FirSimpleFunction,
            newReceiverType: ConeKotlinType? = null,
            newReturnType: ConeKotlinType? = null,
            newParameterTypes: List<ConeKotlinType?>? = null,
            newTypeParameters: List<FirTypeParameter>? = null
        ): FirSimpleFunction {
            // TODO: consider using here some light-weight functions instead of pseudo-real FirMemberFunctionImpl
            // As second alternative, we can invent some light-weight kind of FirRegularClass
            return buildSimpleFunction {
                source = baseFunction.source
                this.session = session
                returnTypeRef = baseFunction.returnTypeRef.withReplacedReturnType(newReturnType)
                receiverTypeRef = baseFunction.receiverTypeRef?.withReplacedConeType(newReceiverType)
                name = baseFunction.name
                status = baseFunction.status
                symbol = fakeOverrideSymbol
                annotations += baseFunction.annotations
                resolvePhase = baseFunction.resolvePhase
                valueParameters += baseFunction.valueParameters.zip(
                    newParameterTypes ?: List(baseFunction.valueParameters.size) { null }
                ) { valueParameter, newType ->
                    buildValueParameter {
                        source = valueParameter.source
                        this.session = session
                        returnTypeRef = valueParameter.returnTypeRef.withReplacedConeType(newType)
                        name = valueParameter.name
                        symbol = FirVariableSymbol(valueParameter.symbol.callableId)
                        defaultValue = valueParameter.defaultValue
                        isCrossinline = valueParameter.isCrossinline
                        isNoinline = valueParameter.isNoinline
                        isVararg = valueParameter.isVararg
                    }
                }

                // TODO: Fix the hack for org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.addFakeOverrides
                // We might have added baseFunction.typeParameters in case new ones are null
                // But it fails at org.jetbrains.kotlin.ir.AbstractIrTextTestCase.IrVerifier.elementsAreUniqueChecker
                // because it shares the same declarations of type parameters between two different two functions
                if (newTypeParameters != null) {
                    typeParameters += newTypeParameters
                }
            }

        }

        fun createFakeOverrideFunction(
            session: FirSession,
            baseFunction: FirSimpleFunction,
            baseSymbol: FirNamedFunctionSymbol,
            newReceiverType: ConeKotlinType? = null,
            newReturnType: ConeKotlinType? = null,
            newParameterTypes: List<ConeKotlinType?>? = null,
            newTypeParameters: List<FirTypeParameter>? = null
        ): FirNamedFunctionSymbol {
            val symbol = FirNamedFunctionSymbol(baseSymbol.callableId, true, baseSymbol)
            createFakeOverrideFunction(
                symbol, session, baseFunction, newReceiverType, newReturnType, newParameterTypes, newTypeParameters
            )
            return symbol
        }

        fun createFakeOverrideProperty(
            session: FirSession,
            baseProperty: FirProperty,
            baseSymbol: FirPropertySymbol,
            newReceiverType: ConeKotlinType? = null,
            newReturnType: ConeKotlinType? = null
        ): FirPropertySymbol {
            val symbol = FirPropertySymbol(baseSymbol.callableId, true, baseSymbol)
            buildProperty {
                source = baseProperty.source
                this.session = session
                returnTypeRef = baseProperty.returnTypeRef.withReplacedReturnType(newReturnType)
                receiverTypeRef = baseProperty.receiverTypeRef?.withReplacedConeType(newReceiverType)
                name = baseProperty.name
                isVar = baseProperty.isVar
                this.symbol = symbol
                isLocal = false
                status = baseProperty.status
                resolvePhase = baseProperty.resolvePhase
                annotations += baseProperty.annotations
            }
            return symbol
        }

    }
}

// Unlike other cases, return types may be implicit, i.e. unresolved
// But in that cases newType should also be `null`
fun FirTypeRef.withReplacedReturnType(newType: ConeKotlinType?): FirTypeRef {
    require(this is FirResolvedTypeRef || newType == null)
    if (newType == null) return this

    return buildResolvedTypeRef {
        source = this@withReplacedReturnType.source
        type = newType
        annotations += this@withReplacedReturnType.annotations
    }
}

fun FirTypeRef.withReplacedConeType(newType: ConeKotlinType?): FirResolvedTypeRef {
    require(this is FirResolvedTypeRef)
    if (newType == null) return this

    return buildResolvedTypeRef {
        source = this@withReplacedConeType.source
        type = newType
        annotations += this@withReplacedConeType.annotations
    }
}
