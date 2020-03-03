/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes

import org.jetbrains.kotlin.fir.declarations.FirCallableMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor

interface FirOverrideChecker {
    fun isOverriddenFunction(
        overrideCandidate: FirSimpleFunction,
        overrideSubstitutor: ConeSubstitutor,
        baseDeclaration: FirSimpleFunction,
        baseSubstitutor: ConeSubstitutor
    ): Boolean

    fun isOverriddenProperty(
        overrideCandidate: FirCallableMemberDeclaration<*>, // NB: in Java it can be a function which overrides accessor
        overrideSubstitutor: ConeSubstitutor,
        baseDeclaration: FirProperty,
        baseSubstitutor: ConeSubstitutor
    ): Boolean
}
