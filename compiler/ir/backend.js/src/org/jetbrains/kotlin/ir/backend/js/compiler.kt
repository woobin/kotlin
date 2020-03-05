/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AbstractAnalyzerWithCompilerReport
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.common.phaser.invokeToplevel
import org.jetbrains.kotlin.backend.common.serialization.knownBuiltins
import org.jetbrains.kotlin.backend.common.serialization.mangle.ManglerChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.Ir2DescriptorManglerAdapter
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.backend.js.lower.generateTests
import org.jetbrains.kotlin.ir.backend.js.lower.moveBodilessDeclarationsToSeparatePlace
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsManglerDesc
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsManglerIr
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformer
import org.jetbrains.kotlin.ir.backend.js.utils.JsMainFunctionDetector
import org.jetbrains.kotlin.ir.backend.js.utils.NameTables
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.StageController
import org.jetbrains.kotlin.ir.declarations.stageController
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.generateTypicalIrProviderList
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.resolver.KotlinLibraryResolveResult
import org.jetbrains.kotlin.name.FqName

class CompilerResult(
    val jsCode: String?,
    val dceJsCode: String?,
    val tsDefinitions: String? = null
)

fun compile(
    project: Project,
    mainModule: MainModule,
    analyzer: AbstractAnalyzerWithCompilerReport,
    configuration: CompilerConfiguration,
    phaseConfig: PhaseConfig,
    allDependencies: KotlinLibraryResolveResult,
    friendDependencies: List<KotlinLibrary>,
    mainArguments: List<String>?,
    exportedDeclarations: Set<FqName> = emptySet(),
    generateFullJs: Boolean = true,
    generateDceJs: Boolean = false,
    dceDriven: Boolean = false
): CompilerResult {
    stageController = StageController()

    val (moduleFragment: IrModuleFragment, dependencyModules, irBuiltIns, symbolTable, deserializer) =
        loadIr(project, mainModule, analyzer, configuration, allDependencies, friendDependencies)

    // TODO: not sure whether this check should be enabled by default. Add configuration key for it.
    if (mainModule is MainModule.SourceFiles) {
        val mangleChecker = ManglerChecker(JsManglerIr, Ir2DescriptorManglerAdapter(JsManglerDesc))
        moduleFragment.acceptVoid(mangleChecker)
        irBuiltIns.knownBuiltins.forEach { it.acceptVoid(mangleChecker) }
    }

    val allModules = when (mainModule) {
        is MainModule.SourceFiles -> dependencyModules + listOf(moduleFragment)
        is MainModule.Klib -> dependencyModules
    }

    // Create context and load declarations referenced during `context` initialization
    val moduleDescriptor = moduleFragment.descriptor
    val context = JsIrBackendContext(moduleDescriptor, irBuiltIns, symbolTable, moduleFragment, exportedDeclarations, configuration)
    allModules.forEach {
        val irProviders = generateTypicalIrProviderList(it.descriptor, irBuiltIns, symbolTable, deserializer)
        ExternalDependenciesGenerator(symbolTable, irProviders).generateUnboundSymbolsAsDependencies()
    }
    allModules.forEach { it.patchDeclarationParents() } // TODO eliminate
    deserializer.finalizeExpectActualLinker()

    val mainFunction = JsMainFunctionDetector.getMainFunctionOrNull(moduleFragment)

    allModules.forEach {
        moveBodilessDeclarationsToSeparatePlace(context, it)
    }

    if (dceDriven) {

        // TODO should be done incrementally
        generateTests(context, moduleFragment)

        val controller = MutableController(context, pirLowerings)
        stageController = controller

        controller.currentStage = controller.lowerings.size + 1

        val irFiles = allModules.flatMap { it.files }
        eliminateDeadDeclarations(irFiles, context, mainFunction)

        // TODO investigate whether this is needed anymore
        stageController = StageController(controller.currentStage)

        moduleFragment.replaceFilesWith(irFiles)
        val transformer = IrModuleToJsTransformer(context, mainFunction, mainArguments)
        return transformer.generateModule(moduleFragment, fullJs = true, dceJs = false)
    } else {

        moduleFragment.replaceFilesWith(allModules.flatMap { it.files })

        jsPhases.invokeToplevel(phaseConfig, context, moduleFragment)
        val transformer = IrModuleToJsTransformer(context, mainFunction, mainArguments)
        return transformer.generateModule(moduleFragment, generateFullJs, generateDceJs)
    }
}

fun IrModuleFragment.replaceFilesWith(irFiles: Collection<IrFile>) {
    files.clear()
    files += irFiles
}

fun generateJsCode(
    context: JsIrBackendContext,
    moduleFragment: IrModuleFragment,
    nameTables: NameTables
): String {
    moveBodilessDeclarationsToSeparatePlace(context, moduleFragment)
    jsPhases.invokeToplevel(PhaseConfig(jsPhases), context, moduleFragment)

    val transformer = IrModuleToJsTransformer(context, null, null, true, nameTables)
    return transformer.generateModule(moduleFragment).jsCode!!
}
