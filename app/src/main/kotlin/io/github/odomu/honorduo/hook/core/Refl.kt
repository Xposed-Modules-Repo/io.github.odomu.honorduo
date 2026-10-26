/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook.core

import android.annotation.SuppressLint
import android.content.res.Resources
import io.github.odomu.honorduo.model.Prefs
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance reflection and ART hidden API bypass utility for LSPosed hooking.
 *
 * Employs multi-tier concurrent caches for classes, fields, and methods to eliminate
 * redundant reflection lookups and stack-trace allocation overhead in high-frequency paths.
 */
internal object Refl {

    // Cache containers using thread-safe concurrent maps
    private val CLASS_CACHE = ConcurrentHashMap<ClassCacheKey, Class<*>>()
    private val FIELD_CACHE = ConcurrentHashMap<FieldCacheKey, Field>()
    private val METHOD_CACHE = ConcurrentHashMap<MethodCacheKey, Method>()
    private val METHOD_ARITY_CACHE = ConcurrentHashMap<MethodArityKey, Method>()
    private val RES_ID_CACHE = ConcurrentHashMap<String, Int>()

    private data class ClassCacheKey(val name: String, val loader: ClassLoader)
    private data class FieldCacheKey(val clazz: Class<*>, val name: String)
    private data class MethodArityKey(val clazz: Class<*>, val name: String, val arity: Int)
    private data class MethodCacheKey(val clazz: Class<*>, val name: String, val sig: Array<out Class<*>>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MethodCacheKey) return false
            if (clazz != other.clazz) return false
            if (name != other.name) return false
            return sig.contentEquals(other.sig)
        }

        override fun hashCode(): Int {
            var result = clazz.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + sig.contentHashCode()
            return result
        }
    }

    init {
        // Exempt all hidden APIs via LSPosed HiddenApiBypass (minSdk is 29)
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    /**
     * Resolves a [Class] by [name] and [cl] with thread-safe caching.
     */
    @JvmStatic
    fun cls(name: String, cl: ClassLoader): Class<*>? {
        val key = ClassCacheKey(name, cl)
        CLASS_CACHE[key]?.let { return it }
        val found = runCatching { Class.forName(name, false, cl) }.getOrNull() ?: return null
        CLASS_CACHE[key] = found
        return found
    }

    /**
     * Finds a declared [Field] in [c] or its superclasses with caching.
     * Leverages [HiddenApiBypass] to avoid throwing [NoSuchFieldException] during hierarchy traversal.
     */
    @JvmStatic
    fun field(c: Class<*>?, name: String): Field? {
        if (c == null) return null
        val key = FieldCacheKey(c, name)
        FIELD_CACHE[key]?.let { return it }

        var cur: Class<*>? = c
        while (cur != null && cur != Any::class.java) {
            val matched = cur.declaredFields.firstOrNull { it.name == name }
                ?: runCatching {
                    HiddenApiBypass.getInstanceFields(cur).filterIsInstance<Field>().firstOrNull { it.name == name }
                        ?: HiddenApiBypass.getStaticFields(cur).filterIsInstance<Field>().firstOrNull { it.name == name }
                }.getOrNull()

            if (matched != null) {
                matched.isAccessible = true
                FIELD_CACHE[key] = matched
                return matched
            }
            cur = cur.superclass
        }
        return null
    }

    /**
     * Resolves a declared [Method] in [c] with caching, prioritizing [HiddenApiBypass].
     */
    @JvmStatic
    fun method(c: Class<*>?, name: String, vararg sig: Class<*>): Method? {
        if (c == null) return null
        val key = MethodCacheKey(c, name, sig)
        METHOD_CACHE[key]?.let { return it }

        val matched = runCatching {
            HiddenApiBypass.getDeclaredMethod(c, name, *sig).apply { isAccessible = true }
        }.getOrNull() ?: runCatching {
            c.getDeclaredMethod(name, *sig).apply { isAccessible = true }
        }.getOrNull() ?: return null

        METHOD_CACHE[key] = matched
        return matched
    }


    /**
     * Resolves a declared [Method] by [name] and argument count [arity] with caching.
     */
    @JvmStatic
    fun methodNamed(c: Class<*>?, name: String, arity: Int): Method? {
        if (c == null) return null
        val key = MethodArityKey(c, name, arity)
        METHOD_ARITY_CACHE[key]?.let { return it }

        var cur: Class<*>? = c
        while (cur != null && cur != Any::class.java) {
            val matched = cur.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == arity }
            if (matched != null) {
                matched.isAccessible = true
                METHOD_ARITY_CACHE[key] = matched
                return matched
            }
            cur = cur.superclass
        }
        return null
    }

    /**
     * Reads value of [f] on [o] safely.
     */
    @JvmStatic
    fun get(f: Field?, o: Any?): Any? {
        if (f == null) return null
        return runCatching { f.get(o) }.getOrNull()
    }

    @JvmStatic
    fun getInt(f: Field?, o: Any?, def: Int): Int = (get(f, o) as? Number)?.toInt() ?: def

    @JvmStatic
    fun getBool(f: Field?, o: Any?, def: Boolean): Boolean = (get(f, o) as? Boolean) ?: def

    /**
     * Sets value of [f] on [o] safely.
     */
    @JvmStatic
    fun set(f: Field?, o: Any?, v: Any?) {
        if (f == null) return
        runCatching { f.set(o, v) }
    }

    /**
     * Invokes [m] on [o] safely with optional arguments.
     */
    @JvmStatic
    fun invoke(m: Method?, o: Any?, vararg args: Any?): Any? {
        if (m == null) return null
        return runCatching {
            if (args.isEmpty()) m.invoke(o) else m.invoke(o, *args)
        }.getOrNull()
    }

    /**
     * Resolves a dynamic resource ID without relying primarily on the discouraged [Resources.getIdentifier].
     *
     * Priority:
     * 1. Direct static field lookup on the target R class (e.g. `com.android.systemui.R$id`, `com.android.internal.R$dimen`), which completely avoids asset table scans and warnings.
     * 2. Fallback to [Resources.getIdentifier] only if reflection fails.
     */
    @JvmStatic
    fun resId(
        res: Resources?,
        name: String,
        defType: String = "id",
        defPackage: String = Prefs.SYSTEMUI_PACKAGE,
        cl: ClassLoader? = null,
    ): Int {
        val key = "$defPackage:$defType/$name"
        RES_ID_CACHE[key]?.let { return it }

        var resolved = 0
        val targetLoader = cl ?: res?.javaClass?.classLoader ?: ClassLoader.getSystemClassLoader()
        val rClassName = (if (defPackage == "android") "com.android.internal.R$" else "$defPackage.R$") + defType
        val rClass = runCatching { Class.forName(rClassName, false, targetLoader) }.getOrNull()
        if (rClass != null) {
            resolved = runCatching {
                rClass.getDeclaredField(name).apply { isAccessible = true }.getInt(null)
            }.getOrDefault(0)
        }

        if (resolved == 0 && res != null) {
            @SuppressLint("DiscouragedApi")
            resolved = runCatching { res.getIdentifier(name, defType, defPackage) }.getOrDefault(0)
        }

        if (resolved != 0) {
            RES_ID_CACHE[key] = resolved
        }
        return resolved
    }
}
