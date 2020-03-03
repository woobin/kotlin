/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirCallableMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.typeContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeContext
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.SimpleTypeMarker

class FirStandardOverrideChecker(session: FirSession) : FirAbstractOverrideChecker() {

    private val context: ConeTypeContext = session.typeContext

    private fun isEqualTypes(candidateType: ConeKotlinType, baseType: ConeKotlinType, substitutor: ConeSubstitutor): Boolean {
        val substitutedCandidateType = substitutor.substituteOrSelf(candidateType)
        val substitutedBaseType = substitutor.substituteOrSelf(baseType)
        return with(context) {
            val baseIsFlexible = substitutedBaseType.isFlexible()
            val candidateIsFlexible = substitutedCandidateType.isFlexible()
            if (baseIsFlexible == candidateIsFlexible) {
                return AbstractTypeChecker.equalTypes(context, substitutedCandidateType, substitutedBaseType)
            }
            val lowerBound: SimpleTypeMarker
            val upperBound: SimpleTypeMarker
            val type: KotlinTypeMarker
            if (baseIsFlexible) {
                lowerBound = substitutedBaseType.lowerBoundIfFlexible()
                upperBound = substitutedBaseType.upperBoundIfFlexible()
                type = substitutedCandidateType
            } else {
                lowerBound = substitutedCandidateType.lowerBoundIfFlexible()
                upperBound = substitutedCandidateType.upperBoundIfFlexible()
                type = substitutedBaseType
            }
            AbstractTypeChecker.isSubtypeOf(context, lowerBound, type) && AbstractTypeChecker.isSubtypeOf(context, type, upperBound)
        }
    }

    override fun isEqualTypes(
        candidateTypeRef: FirTypeRef,
        overrideSubstitutor: ConeSubstitutor,
        baseTypeRef: FirTypeRef,
        baseSubstitutor: ConeSubstitutor,
        substitutor: ConeSubstitutor
    ): Boolean = isEqualTypes(
        (candidateTypeRef as FirResolvedTypeRef).type.let(overrideSubstitutor::substituteOrSelf),
        (baseTypeRef as FirResolvedTypeRef).type.let(baseSubstitutor::substituteOrSelf),
        substitutor
    )

    private fun isEqualReceiverTypes(
        candidateTypeRef: FirTypeRef?,
        overrideSubstitutor: ConeSubstitutor,
        baseTypeRef: FirTypeRef?,
        baseSubstitutor: ConeSubstitutor,
        substitutor: ConeSubstitutor
    ): Boolean {
        return when {
            candidateTypeRef != null && baseTypeRef != null ->
                isEqualTypes(candidateTypeRef, overrideSubstitutor, baseTypeRef, baseSubstitutor, substitutor)
            else -> candidateTypeRef == null && baseTypeRef == null
        }
    }

    override fun isOverriddenFunction(
        overrideCandidate: FirSimpleFunction,
        overrideSubstitutor: ConeSubstitutor,
        baseDeclaration: FirSimpleFunction,
        baseSubstitutor: ConeSubstitutor
    ): Boolean {
        if (overrideCandidate.valueParameters.size != baseDeclaration.valueParameters.size) return false

        val substitutor =
            getSubstitutorIfTypeParametersAreCompatible(overrideCandidate, overrideSubstitutor, baseDeclaration, baseSubstitutor)
                ?: return false

        if (!isEqualReceiverTypes(
                overrideCandidate.receiverTypeRef, overrideSubstitutor,
                baseDeclaration.receiverTypeRef, baseSubstitutor,
                substitutor
            )
        ) return false

        return overrideCandidate.valueParameters.zip(baseDeclaration.valueParameters).all { (memberParam, selfParam) ->
            isEqualTypes(memberParam.returnTypeRef, overrideSubstitutor, selfParam.returnTypeRef, baseSubstitutor, substitutor)
        }

    }

    override fun isOverriddenProperty(
        overrideCandidate: FirCallableMemberDeclaration<*>,
        overrideSubstitutor: ConeSubstitutor,
        baseDeclaration: FirProperty,
        baseSubstitutor: ConeSubstitutor
    ): Boolean {
        // TODO: substitutor
        return overrideCandidate is FirProperty &&
                isEqualReceiverTypes(
                    overrideCandidate.receiverTypeRef, overrideSubstitutor,
                    baseDeclaration.receiverTypeRef, baseSubstitutor,
                    ConeSubstitutor.Empty
                )
    }
}
