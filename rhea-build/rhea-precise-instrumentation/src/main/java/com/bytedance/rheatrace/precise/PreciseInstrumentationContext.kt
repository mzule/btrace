/*
 * Copyright (C) 2021 ByteDance Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bytedance.rheatrace.precise

import com.android.build.gradle.AppExtension
import com.android.builder.model.AndroidProject
import com.bytedance.rheatrace.common.retrace.MappingCollector
import com.bytedance.rheatrace.common.retrace.MappingReader
import com.bytedance.rheatrace.precise.extension.PreciseInstrumentationExtension
import com.bytedance.rheatrace.precise.method.EvilMethodDetector
import com.bytedance.rheatrace.precise.method.EvilMethodInfo
import com.bytedance.rheatrace.precise.method.EvilRootMethodDetector
import com.bytedance.rheatrace.precise.method.MethodHelper
import com.google.common.base.Joiner
import com.ss.android.ugc.bytex.common.BaseContext
import org.gradle.api.Project
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileWriter

/**
 * @author majun
 * @date 2022/3/10
 */
class PreciseInstrumentationContext(project: Project, android: AppExtension, extension: PreciseInstrumentationExtension) :
    BaseContext<PreciseInstrumentationExtension>(project, android, extension) {
    private lateinit var methodHelper: MethodHelper
    private lateinit var evilMethodDetector: EvilMethodDetector
    lateinit var evilRootMethodDetector: EvilRootMethodDetector
    var mappingCollector: MappingCollector? = null

    override fun init() {
        initMappingCollector()
        methodHelper = MethodHelper(this)
        evilRootMethodDetector = EvilRootMethodDetector()
        evilMethodDetector = EvilMethodDetector(methodHelper, evilRootMethodDetector)
    }

    private fun initMappingCollector() {
        if (mappingCollector != null) {
            return
        }
        mappingCollector = MappingCollector()
        val mappingFile = File(getMappingDir(), "mapping.txt")
        if (mappingFile.isFile) {
            val mappingReader = MappingReader(mappingFile)
            mappingReader.read(mappingCollector!!)
        }
    }

    private fun getMappingDir(): String {
        return if (transformContext.variant.buildType.isMinifyEnabled) {
            transformContext.variant.mappingFile.parent
        } else {
            Joiner.on(File.separatorChar).join(
                project.buildDir.absolutePath,
                AndroidProject.FD_OUTPUTS,
                "mapping",
                transformContext.variantName
            )
        }
    }

    fun traverse(node: ClassNode) {
        evilRootMethodDetector.collect(node)
        evilMethodDetector.collect(node)
    }

    fun beforeTransform() {
        evilMethodDetector.findEvilMethods().apply {
            changeRheaFilter(this)
        }
    }

    private fun changeRheaFilter(evilMethodList: List<EvilMethodInfo>) {
        if (extension.traceFilter.isNullOrEmpty()) {
            throw Exception("evil method: rheaTraceFilter isNullOrEmpty")
        }
        val evilMethodMap = hashMapOf<String, ArrayList<EvilMethodInfo>>()
        evilMethodList.forEach { evilMethodInfo ->
            evilMethodMap.computeIfAbsent(evilMethodInfo.className) {
                ArrayList()
            }.add(evilMethodInfo)
        }
        FileWriter(extension.traceFilter!!, true).use { writer ->
            writer.write("\n")
            evilMethodMap.forEach { (key, list) ->
                writer.write("\n")
                val className = key.replace("/", ".")
                writer.write("-traceclassmethods $className {\n")
                list.forEach {
                    writer.write("   ${it.methodName} ${it.desc}\n")
                }
                writer.write("}\n")
            }
        }
    }

    override fun releaseContext() {
        super.releaseContext()
        mappingCollector?.release()
        evilRootMethodDetector.release()
        evilMethodDetector.release()
        methodHelper.release()
    }
}