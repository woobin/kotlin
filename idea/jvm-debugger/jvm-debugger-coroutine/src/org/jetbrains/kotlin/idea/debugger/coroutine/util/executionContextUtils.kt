/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.util

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext

fun ExecutionContext.invokeMethodAsString(instance: ObjectReference, methodName: String): String? {
    return (findAndInvoke(instance, instance.referenceType(), methodName, "()Ljava/lang/String;") as? StringReference)?.value()
}

fun ExecutionContext.invokeMethodAsInt(instance: ObjectReference, methodName: String): Int? {
    return (findAndInvoke(instance, instance.referenceType(), methodName, "()I") as? IntegerValue)?.value()
}

fun ExecutionContext.invokeMethodAsObject(type: ClassType, methodName: String, vararg params: Value): ObjectReference? {
    return invokeMethodAsObject(type, methodName, null, *params)
}

fun ExecutionContext.invokeMethodAsObject(
    type: ClassType,
    methodName: String,
    methodSignature: String?,
    vararg params: Value
): ObjectReference? {
    return findAndInvoke(type, methodName, methodSignature, *params) as? ObjectReference
}

fun ExecutionContext.invokeMethodAsObject(instance: ObjectReference, method: Method, vararg params: Value): ObjectReference? =
    invokeMethod(instance, method, params.asList()) as? ObjectReference

fun ExecutionContext.invokeMethodAsVoid(
    instance: ObjectReference,
    methodName: String,
    methodSignature: String? = null,
    vararg params: Value = emptyArray()
) =
    findAndInvoke(instance, methodName, methodSignature, *params)

fun ExecutionContext.invokeMethodAsArray(
    instance: ClassType,
    methodName: String,
    methodSignature: String,
    vararg params: Value
): ArrayReference? {
    return findAndInvoke(instance, methodName, methodSignature, *params) as? ArrayReference
}

private fun ExecutionContext.findAndInvoke(
    ref: ObjectReference,
    type: ReferenceType,
    name: String,
    methodSignature: String,
    vararg params: Value
): Value? {
    val method = type.methodsByName(name, methodSignature).single()
    return invokeMethod(ref, method, params.asList())
}

fun ExecutionContext.findAndInvoke(type: ClassType, name: String, methodSignature: String? = null, vararg params: Value): Value? {
    val method = when {
        methodSignature != null -> type.methodsByName(name, methodSignature).single()
        else -> type.methodsByName(name).single()
    }

    return invokeMethod(type, method, params.asList())
}

fun ExecutionContext.findAndInvoke(instance: ObjectReference, name: String, methodSignature: String? = null, vararg params: Value): Value? {
    val type = instance.referenceType()
    val method = when {
        methodSignature != null -> type.methodsByName(name, methodSignature).single()
        else -> type.methodsByName(name).single()
    }

    return invokeMethod(instance, method, params.asList())
}