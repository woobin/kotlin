/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext

class CoroutineNoLibraryProxy(val executionContext: DefaultExecutionContext) : CoroutineInfoProvider {
    override fun dumpCoroutinesInfo(): List<CoroutineInfoData> {
        val allClasses = executionContext.vm.allClasses()
        return emptyList()
    }

}