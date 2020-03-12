/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.PsiSourceManager

class Fir2IrConverter(
    private val moduleDescriptor: FirModuleDescriptor,
    private val sourceManager: PsiSourceManager,
    private val declarationStorage: Fir2IrDeclarationStorage
) {

    fun processLocalClassAndNestedClasses(regularClass: FirRegularClass, parent: IrDeclarationParent) {
        val irClass = registerClassAndNestedClasses(regularClass, parent)
        processClassAndNestedClassHeaders(regularClass)
        processClassMembers(regularClass, irClass)
    }

    fun registerFileAndClasses(file: FirFile) {
        val irFile = IrFileImpl(
            sourceManager.getOrCreateFileEntry(file.psi as KtFile),
            moduleDescriptor.getPackage(file.packageFqName).fragments.first()
        )
        declarationStorage.registerFile(file, irFile)
        file.declarations.forEach {
            if (it is FirRegularClass) {
                registerClassAndNestedClasses(it, irFile)
            }
        }
    }

    fun processClassHeaders(file: FirFile) {
        file.declarations.forEach {
            if (it is FirRegularClass) {
                processClassAndNestedClassHeaders(it)
            }
        }
    }

    fun processFileAndClassMembers(file: FirFile) {
        val irFile = declarationStorage.getIrFile(file)
        for (declaration in file.declarations) {
            val irDeclaration = processMemberDeclaration(declaration, irFile) ?: continue
            irFile.declarations += irDeclaration
        }
    }

    fun processAnonymousObjectMembers(
        anonymousObject: FirAnonymousObject,
        irClass: IrClass = declarationStorage.getCachedIrClass(anonymousObject)!!
    ): IrClass {
        anonymousObject.getPrimaryConstructorIfAny()?.let {
            irClass.declarations += declarationStorage.createIrConstructor(it, irClass)
        }
        for (declaration in anonymousObject.declarations) {
            if (declaration is FirRegularClass) {
                registerClassAndNestedClasses(declaration, irClass)
                processClassAndNestedClassHeaders(declaration)
            }
            val irDeclaration = processMemberDeclaration(declaration, irClass) ?: continue
            irClass.declarations += irDeclaration
        }
        return irClass
    }

    private fun processClassMembers(
        regularClass: FirRegularClass,
        irClass: IrClass = declarationStorage.getCachedIrClass(regularClass)!!
    ): IrClass {
        regularClass.getPrimaryConstructorIfAny()?.let {
            irClass.declarations += declarationStorage.createIrConstructor(it, irClass)
        }
        for (declaration in regularClass.declarations) {
            val irDeclaration = processMemberDeclaration(declaration, irClass) ?: continue
            irClass.declarations += irDeclaration
        }
        return irClass
    }

    private fun registerClassAndNestedClasses(regularClass: FirRegularClass, parent: IrDeclarationParent): IrClass {
        val irClass = declarationStorage.registerIrClass(regularClass, parent)
        regularClass.declarations.forEach {
            if (it is FirRegularClass) {
                registerClassAndNestedClasses(it, irClass)
            }
        }
        return irClass
    }

    private fun processClassAndNestedClassHeaders(regularClass: FirRegularClass) {
        declarationStorage.processClassHeader(regularClass)
        regularClass.declarations.forEach {
            if (it is FirRegularClass) {
                processClassAndNestedClassHeaders(it)
            }
        }
    }

    private fun processMemberDeclaration(declaration: FirDeclaration, parent: IrDeclarationParent): IrDeclaration? {
        return when (declaration) {
            is FirRegularClass -> {
                processClassMembers(declaration)
            }
            is FirSimpleFunction -> {
                declarationStorage.createIrFunction(declaration, parent)
            }
            is FirProperty -> {
                declarationStorage.createIrProperty(declaration, parent)
            }
            is FirConstructor -> if (!declaration.isPrimary) {
                declarationStorage.createIrConstructor(declaration, parent as IrClass)
            } else {
                null
            }
            is FirEnumEntry -> {
                declarationStorage.createIrEnumEntry(declaration, parent as IrClass)
            }
            is FirAnonymousInitializer -> {
                declarationStorage.createIrAnonymousInitializer(declaration, parent as IrClass)
            }
            is FirTypeAlias -> {
                // DO NOTHING
                null
            }
            else -> {
                throw AssertionError("Unexpected member: ${declaration::class}")
            }
        }
    }

    companion object {
        fun createModuleFragment(
            session: FirSession,
            firFiles: List<FirFile>,
            languageVersionSettings: LanguageVersionSettings,
            fakeOverrideMode: FakeOverrideMode = FakeOverrideMode.NORMAL,
            signaturer: IdSignatureComposer
        ): Fir2IrResult {
            val moduleDescriptor = FirModuleDescriptor(session)
            val symbolTable = SymbolTable(signaturer)
            val constantValueGenerator = ConstantValueGenerator(moduleDescriptor, symbolTable)
            val typeTranslator = TypeTranslator(symbolTable, languageVersionSettings, moduleDescriptor.builtIns)
            constantValueGenerator.typeTranslator = typeTranslator
            typeTranslator.constantValueGenerator = constantValueGenerator
            val builtIns = IrBuiltIns(moduleDescriptor.builtIns, typeTranslator, signaturer, symbolTable)
            val sourceManager = PsiSourceManager()
            val declarationStorage = Fir2IrDeclarationStorage(session, symbolTable, moduleDescriptor)
            val typeConverter = Fir2IrTypeConverter(session, declarationStorage, builtIns)
            declarationStorage.typeConverter = typeConverter
            typeConverter.initBuiltinTypes()
            val irFiles = mutableListOf<IrFile>()

            val converter = Fir2IrConverter(moduleDescriptor, sourceManager, declarationStorage)
            for (firFile in firFiles) {
                converter.registerFileAndClasses(firFile)
            }
            for (firFile in firFiles) {
                converter.processClassHeaders(firFile)
            }
            for (firFile in firFiles) {
                converter.processFileAndClassMembers(firFile)
            }

            val fir2irVisitor = Fir2IrVisitor(
                session, symbolTable, declarationStorage, converter, typeConverter, builtIns, fakeOverrideMode
            )
            for (firFile in firFiles) {
                val irFile = firFile.accept(fir2irVisitor, null) as IrFile
                val fileEntry = sourceManager.getOrCreateFileEntry(firFile.psi as KtFile)
                sourceManager.putFileEntry(irFile, fileEntry)
                irFiles += irFile
            }

            val irModuleFragment = IrModuleFragmentImpl(moduleDescriptor, builtIns, irFiles)
            generateUnboundSymbolsAsDependencies(irModuleFragment, symbolTable, builtIns)
            return Fir2IrResult(irModuleFragment, symbolTable, sourceManager)
        }

        private fun generateUnboundSymbolsAsDependencies(
            irModule: IrModuleFragment,
            symbolTable: SymbolTable,
            builtIns: IrBuiltIns
        ) {
            // TODO: provide StubGeneratorExtensions for correct lazy stub IR generation on JVM
            ExternalDependenciesGenerator(symbolTable, generateTypicalIrProviderList(irModule.descriptor, builtIns, symbolTable))
                .generateUnboundSymbolsAsDependencies()
        }
    }
}