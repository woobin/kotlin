/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.load.java.sam

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.synthetic.FunctionInterfaceConstructorDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude

interface SamConstructorDescriptor : SimpleFunctionDescriptor, FunctionInterfaceConstructorDescriptor

class SamConstructorDescriptorImpl(
    containingDeclaration: DeclarationDescriptor,
    private val samInterface: ClassDescriptor
) : SimpleFunctionDescriptorImpl(
    containingDeclaration,
    null,
    samInterface.annotations,
    samInterface.name,
    CallableMemberDescriptor.Kind.SYNTHESIZED,
    samInterface.source
), SamConstructorDescriptor {
    override val baseDescriptorForSynthetic: ClassDescriptor
        get() = samInterface
}

object SamConstructorDescriptorKindExclude : DescriptorKindExclude() {
    override fun excludes(descriptor: DeclarationDescriptor) = descriptor is SamConstructorDescriptor

    override val fullyExcludedDescriptorKinds: Int get() = 0
}
